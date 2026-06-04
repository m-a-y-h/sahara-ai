"""Sahara AI chat endpoint on Modal — direct featherless chat-completions.

Token generation goes straight to featherless-ai's chat-completions endpoint
through HuggingFace's router, with a tight system prompt and the per-session
conversation history. The heavyweight ``sahara_ai_protocol.sahara_ai_chat``
pipeline (assessment -> system-prompt builder -> normaliser) was suppressing
Qalb's actual replies on the FYP timeline: substance keywords routed to a
canned acknowledgement, and the response normaliser would frequently drop
Qalb's output for failing one of its many heuristic checks. This rewrite
keeps Qalb's NLP front-and-centre and only adds a minimal heuristic for
``trigger_counselor`` so the in-app "Talk to a counselor" attachment still
fires on explicit crisis text.

Setup (one-time):
    modal secret create huggingface-secret HF_TOKEN=<your hf token>

Deploy — from the ``services/`` directory so ``add_local_python_source
("sahara_ai")`` can still resolve the protocol module (kept around for the
summariser):
    cd services && modal deploy sahara_ai/modal_deploy.py

Modal prints an https URL. Set it in the Android app's ``local.properties``:
    sahara.ai.chat.url=<that url>/v1/chat
"""

import os
import re

import modal

APP_NAME = "sahara-ai"
# Default model swapped from enstazao/Qalb-1.0-8B-Instruct to Meta's
# Llama-3.1-70B-Instruct (also served by featherless via HF's router).
# Qalb is an 8B Urdu fine-tune that was getting genuinely confused by
# colloquial Roman Urdu — it interpreted "khrab" (bad/broken) as a
# reference to a broken shop ("Aap kis dukaan me ja rahay hain?"). The
# 70B Llama gives coherent, empathic Roman Urdu replies on the exact
# same input in side-by-side tests:
#   Qalb-8B   : "Aap kis dukaan me ja rahay hain? Khrab karna bura nahi..."
#   Llama-70B : "samajh aya, tumhein lagta hai ke tum theek nahi ho aur
#                tumhara gala kharab hai. Kya yeh kuch dinon se ho raha
#                hai ya achanak se shuru hua hai?"
# Override via SAHARA_AI_MODEL_ID on the Modal Secret if needed.
DEFAULT_MODEL_ID = "meta-llama/Meta-Llama-3.1-70B-Instruct"
# featherless-ai serves both Llama-3.1-70B-Instruct and the Qalb fine-tune
# through HF's router. We use the same chat-completions endpoint and just
# swap the model id, so failing back to Qalb (or any other featherless-
# hosted model) is just a Modal Secret edit.
FEATHERLESS_URL = (
    "https://router.huggingface.co/featherless-ai/v1/chat/completions"
)

# Crisis vocabulary; if any term matches the user's latest message, the
# response flips trigger_counselor=true so the Android chat UI attaches the
# "Talk to a counselor" inline crisis card to the bot bubble. Bilingual on
# purpose — the user types Roman Urdu freely.
CRISIS_TERMS = (
    "suicide", "khudkushi", "khud kushi", "self harm", "self-harm", "kill myself",
    "marna chahta", "marna chahti", "marna chahti hoon", "marna chahta hoon",
    "overdose", "od kiya", "blue lips", "neelay hont", "neele hont",
    "fainting", "behosh", "behoshi", "saans nahi", "saans nahi aa",
    "chest pain", "seene mein dard", "fit aya", "fit aaya",
    "withdrawal", "withdrawals", "shaking", "kaanp",
)

# Substance vocabulary, used to populate `substance_detected` for analytics
# / dashboard. Doesn't gate the reply.
SUBSTANCE_TERMS = {
    "alcohol": ("sharab", "shrb", "shrab", "alcohol", "wine", "beer", "vodka", "whisky", "whiskey", "rum"),
    "cannabis": ("charas", "chrs", "weed", "cannabis", "ganja", "hashish", "marijuana", "joint"),
    "ice": ("ice", "crystal meth", "meth", "shabu"),
    "heroin": ("heroin", "smack", "brown sugar"),
    "cocaine": ("cocaine", "coke", "blow"),
    "pills": ("xanax", "valium", "rohypnol", "tramadol", "tablet", "pills"),
}

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "fastapi==0.115.0",
        "pydantic==2.9.2",
        "httpx==0.27.2",
    )
    .env({"TOKENIZERS_PARALLELISM": "false"})
)

app = modal.App(name=APP_NAME, image=image)


@app.function(
    timeout=120,
    scaledown_window=300,
    secrets=[modal.Secret.from_name("huggingface-secret", required_keys=["HF_TOKEN"])],
)
@modal.asgi_app(label="chat-endpoint")
def fastapi_app():
    import time

    import httpx
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel

    model_id = os.environ.get("SAHARA_AI_MODEL_ID", DEFAULT_MODEL_ID)
    hf_token = os.environ["HF_TOKEN"]

    # One shared httpx client so connection pooling is reused across
    # cold-start-warm container lifetime.
    http = httpx.Client(timeout=60.0)

    def call_qalb(messages: list, max_tokens: int = 512) -> str:
        """POST to featherless via HF router with one retry on transient failure."""
        body = {
            "model": model_id,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": 0.6,
            "top_p": 0.95,
            "frequency_penalty": 0.5,  # OpenAI-style; better cycle breaker than repetition_penalty
            "presence_penalty": 0.3,
            "stream": False,
        }
        headers = {
            "Authorization": f"Bearer {hf_token}",
            "Content-Type": "application/json",
        }
        last_exc: Exception | None = None
        for attempt in range(2):
            try:
                resp = http.post(FEATHERLESS_URL, headers=headers, json=body)
                if resp.status_code == 429 or 500 <= resp.status_code < 600:
                    raise httpx.HTTPStatusError(
                        f"upstream {resp.status_code}: {resp.text[:300]}",
                        request=resp.request, response=resp,
                    )
                resp.raise_for_status()
                data = resp.json()
                content = (
                    data.get("choices", [{}])[0]
                        .get("message", {})
                        .get("content", "")
                ) or ""
                content = content.strip()
                if content:
                    return content
                last_exc = RuntimeError("empty content from upstream")
            except Exception as exc:
                last_exc = exc
            if attempt == 0:
                time.sleep(1.2)
        raise RuntimeError(f"Qalb upstream failed: {last_exc}")

    def detect_substance(text: str) -> str:
        low = text.lower()
        for key, terms in SUBSTANCE_TERMS.items():
            if any(re.search(rf"\b{re.escape(t)}\b", low) for t in terms):
                return key
        return ""

    def detect_crisis(text: str) -> bool:
        low = text.lower()
        return any(term in low for term in CRISIS_TERMS)

    def system_prompt(language_hint: str, prior_summaries: list[str]) -> str:
        parts = [
            # Identity + role.
            "You are SAHARA, a warm bilingual companion for Pakistani users who may be "
            "dealing with substance use, stress, or low mood. You are NOT a doctor.",
            # Hard rules — phrased as imperatives because this is an 8B
            # fine-tune that drifts when given soft suggestions.
            "RULES (follow strictly):\n"
            "1. Reply with 2 to 4 short, natural sentences. Never one-liners.\n"
            "2. Match the user's language exactly. If they wrote Roman Urdu, reply in Roman Urdu (Hindi-Urdu in Latin letters). If English, English. If mixed, mix the same way. NEVER use Urdu script.\n"
            "3. NEVER greet the user. NEVER say 'Salam', 'Hello', 'Hi', or introduce yourself. You are already in conversation.\n"
            "4. NEVER repeat your previous reply or re-ask a question the user already answered.\n"
            "5. If the user's message is short or ambiguous (e.g. 'gla khrab ha', '?', 'ok'), DO NOT ask 'what danger are you in' or generic questions. Instead, gently ask a CONCRETE follow-up about THIS message (e.g. 'kab se khrab hai gala? Kuch liya hai aaj?').\n"
            "6. NEVER give medical advice, dosages, diagnoses, or recovery protocols. NEVER moralise about substance use.\n"
            "7. Acknowledge what the user said in your own words first, THEN ask one focused follow-up question.",
            # Crisis branch.
            "If the user mentions chest pain, fainting, blue lips, a fit, no breathing, suicidal thoughts, "
            "or self-harm, reply warmly and add ONE line asking them to open the counselor list or press "
            "the Emergency button right now. Do not panic them.",
        ]
        if language_hint == "english":
            parts.append("Reply in English this turn.")
        elif language_hint == "roman_urdu":
            parts.append("Reply in Roman Urdu this turn (Hindi-Urdu in Latin letters, no Urdu script).")
        if prior_summaries:
            joined = " | ".join(s.strip() for s in prior_summaries if s.strip())
            if joined:
                parts.append(
                    "Earlier in this conversation (compressed summaries, oldest first; "
                    "treat these as already-established background, do NOT mention them explicitly): "
                    + joined
                )
        return "\n\n".join(parts)

    def summarise_system_prompt(language_hint: str) -> str:
        return (
            "You are compressing a Sahara mental-health chat into 3-5 short sentences. "
            "Preserve: any substance mentioned (name, frequency, context), risk signals "
            "(suicidal thoughts, withdrawal, self-harm), the user's emotional state, and "
            "the assistant's last guidance. Use the same language the user used "
            f"({language_hint or 'auto'}). No new advice — just the compressed factual summary."
        )

    api = FastAPI(title="Sahara AI (Modal -> featherless)", version="0.5.0")

    class HistoryTurn(BaseModel):
        role: str
        content: str
        timestamp_ms: int | None = None

    class ChatRequest(BaseModel):
        user_input: str
        language: str | None = None
        is_english: bool | None = None
        history: list[HistoryTurn] | None = None
        prior_summaries: list[str] | None = None

    class SummarizeRequest(BaseModel):
        messages: list[HistoryTurn]
        language: str | None = None

    @api.get("/")
    def root() -> dict:
        return {
            "service": APP_NAME,
            "host": "modal",
            "backend": "featherless-via-hf-router",
            "model": model_id,
            "endpoints": ["/v1/chat", "/v1/summarize", "/healthz"],
        }

    @api.get("/healthz")
    def healthz() -> dict:
        return {"ok": True}

    @api.post("/v1/chat")
    def chat(req: ChatRequest) -> dict:
        text = (req.user_input or "").strip()
        if not text:
            raise HTTPException(status_code=400, detail="user_input must not be empty.")

        language_hint = (req.language or "").strip().lower()
        if req.is_english is True and not language_hint:
            language_hint = "english"

        messages = [{"role": "system", "content": system_prompt(language_hint, list(req.prior_summaries or []))}]
        for turn in (req.history or []):
            role = turn.role if turn.role in ("user", "assistant") else "user"
            content = (turn.content or "").strip()
            if content:
                messages.append({"role": role, "content": content})
        messages.append({"role": "user", "content": text})

        try:
            reply = call_qalb(messages, max_tokens=512)
        except Exception as exc:
            # Hand back an empty reply field — the Android client falls
            # back to its localised "couldn't reach Sahara AI" line in
            # that case (better than synthesising fake guidance here).
            return {"reply": "", "error": str(exc)[:200], "trigger_counselor": False}

        substance = detect_substance(text)
        is_crisis = detect_crisis(text)
        return {
            "reply": reply,
            "trigger_counselor": is_crisis,
            "substance_detected": substance,
            "substances_detected": [substance] if substance else [],
            "risk_level": "high" if is_crisis else ("elevated" if substance else "low"),
            "message_type": "CRISIS_CARD" if is_crisis else "TEXT",
            "action_destination": "counselors" if is_crisis else None,
            "quick_replies": [],
            "safety_flags": (["crisis"] if is_crisis else []) + ([substance] if substance else []),
            "detected_symptoms": [],
            "user_intent": "support_request",
        }

    @api.post("/v1/summarize")
    def summarize(req: SummarizeRequest) -> dict:
        if not req.messages:
            raise HTTPException(status_code=400, detail="messages must not be empty.")
        language_hint = (req.language or "").strip().lower()
        transcript_lines = []
        for t in req.messages:
            speaker = "User" if t.role == "user" else "Assistant"
            transcript_lines.append(f"{speaker}: {t.content}")
        transcript = "\n".join(transcript_lines)
        messages = [
            {"role": "system", "content": summarise_system_prompt(language_hint)},
            {"role": "user", "content": transcript},
        ]
        try:
            summary = call_qalb(messages, max_tokens=320)
        except Exception as exc:
            return {"summary": "", "error": str(exc)[:200]}
        return {"summary": summary}

    return api
