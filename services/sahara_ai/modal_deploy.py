"""Sahara AI chat endpoint on Modal — Google Gemini 2.5 Flash backend.

Token generation goes to Google's Generative Language API
(``generativelanguage.googleapis.com``) using a free-tier ``GEMINI_API_KEY``
from https://aistudio.google.com/app/apikey. Free tier ships with ~15 RPM
and ~1M input tokens / day on ``gemini-2.5-flash``, more than enough for
an FYP demo.

Why Gemini instead of featherless/Qalb/Llama-3.1-70B:
  - Qalb-1.0-8B-Instruct (Urdu fine-tune) hallucinated dukaans for
    "gla khrab" and re-introduced itself between turns.
  - Llama-3.1-8B-Instruct on featherless was passable but inconsistent.
  - Llama-3.1-70B-Instruct on featherless was strong on single turns but
    free-tier rate-limited under demo load.
  - Gemini 2.5 Flash handles colloquial Roman Urdu cleanly, holds
    multi-turn context, and the free-tier quota is hands-down the
    most forgiving of all the options we tested.

One-time setup:
  1. Generate a key: https://aistudio.google.com/app/apikey
  2. modal secret create gemini-secret GEMINI_API_KEY=<that key>
  3. cd services && modal deploy sahara_ai/modal_deploy.py
  4. Set sahara.ai.chat.url in local.properties to <printed-url>/v1/chat

The Android client's wire format (SaharaAiClient.kt) is unchanged —
{user_input, language, history[role/content/timestamp_ms],
prior_summaries[]} in and {reply, trigger_counselor, ...} out — so no
APK rebuild is strictly required if the chat URL hasn't moved.
"""

import json
import os
import re

import modal

APP_NAME = "sahara-ai"
# Default model. SAHARA_AI_MODEL_ID on the Modal Secret overrides for
# trivial swaps (e.g. "gemini-2.5-pro" if quotas allow).
DEFAULT_MODEL_ID = "gemini-2.5-flash"
GEMINI_ENDPOINT = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "{model}:generateContent?key={key}"
)

# Crisis vocabulary; any hit on the user's latest message flips
# trigger_counselor=true so the Android inline crisis attachment fires.
CRISIS_TERMS = (
    "suicide", "khudkushi", "khud kushi", "self harm", "self-harm", "kill myself",
    "marna chahta", "marna chahti", "marna chahti hoon", "marna chahta hoon",
    "overdose", "od kiya", "blue lips", "neelay hont", "neele hont",
    "fainting", "behosh", "behoshi", "saans nahi", "saans nahi aa",
    "chest pain", "seene mein dard", "fit aya", "fit aaya",
    "withdrawal", "withdrawals", "shaking", "kaanp",
)

# Substance vocabulary, used to populate `substance_detected`. Doesn't
# gate the reply — model still answers the user.
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
    secrets=[modal.Secret.from_name("gemini-secret", required_keys=["GEMINI_API_KEY"])],
)
@modal.asgi_app(label="chat-endpoint")
def fastapi_app():
    import time

    import httpx
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel

    model_id = os.environ.get("SAHARA_AI_MODEL_ID", DEFAULT_MODEL_ID)
    api_key = os.environ["GEMINI_API_KEY"]
    endpoint_url = GEMINI_ENDPOINT.format(model=model_id, key=api_key)

    http = httpx.Client(timeout=60.0)

    def to_gemini_contents(messages: list[dict]) -> tuple[str, list[dict]]:
        """Split OpenAI-style {role,content} messages into Gemini's
        (systemInstruction, contents[]) shape. Gemini uses "user" and
        "model" roles (not "assistant"), and the system message goes in
        a separate top-level field."""
        system_text_parts: list[str] = []
        contents: list[dict] = []
        for m in messages:
            role = m.get("role", "user")
            text = (m.get("content") or "").strip()
            if not text:
                continue
            if role == "system":
                system_text_parts.append(text)
            else:
                gem_role = "model" if role == "assistant" else "user"
                contents.append({"role": gem_role, "parts": [{"text": text}]})
        return ("\n\n".join(system_text_parts), contents)

    def call_gemini(messages: list[dict], max_tokens: int = 512) -> str:
        """POST to Gemini's generateContent with one retry on transient failure."""
        system_text, contents = to_gemini_contents(messages)
        payload: dict = {
            "contents": contents,
            "generationConfig": {
                "temperature": 0.6,
                "topP": 0.95,
                "maxOutputTokens": max_tokens,
            },
            # Loosen safety filters — mental-health / substance-use chat
            # legitimately mentions drugs, suicidal feelings, etc. With
            # default thresholds Gemini will refuse to respond to those
            # turns. BLOCK_NONE means *we* decide what to do via the
            # crisis-card heuristic, the model doesn't pre-empt the
            # conversation.
            "safetySettings": [
                {"category": c, "threshold": "BLOCK_NONE"} for c in (
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT",
                )
            ],
        }
        if system_text:
            payload["systemInstruction"] = {"parts": [{"text": system_text}]}

        last_exc: Exception | None = None
        for attempt in range(2):
            try:
                resp = http.post(
                    endpoint_url,
                    headers={"Content-Type": "application/json"},
                    json=payload,
                )
                if resp.status_code == 429 or 500 <= resp.status_code < 600:
                    raise httpx.HTTPStatusError(
                        f"upstream {resp.status_code}: {resp.text[:300]}",
                        request=resp.request, response=resp,
                    )
                resp.raise_for_status()
                data = resp.json()
                # Gemini schema:
                #   { "candidates": [{ "content": { "parts": [{"text": ...}] } }], ... }
                cand = (data.get("candidates") or [{}])[0]
                parts = (cand.get("content") or {}).get("parts") or []
                text = "".join(p.get("text", "") for p in parts).strip()
                if text:
                    return text
                # Empty body — possibly blocked by a residual safety
                # filter. Surface a useful exception so the caller can
                # fall back rather than silently returning nothing.
                finish = cand.get("finishReason", "UNKNOWN")
                last_exc = RuntimeError(f"Gemini returned empty text (finishReason={finish})")
            except Exception as exc:
                last_exc = exc
            if attempt == 0:
                time.sleep(1.2)
        raise RuntimeError(f"Gemini upstream failed: {last_exc}")

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
            "You are SAHARA, a warm bilingual companion for Pakistani users who may be "
            "dealing with substance use, stress, or low mood. You are NOT a doctor.",
            "RULES (follow strictly):\n"
            "1. Reply with 2 to 4 short, natural sentences. Never one-liners.\n"
            "2. Match the user's language exactly. If they wrote Roman Urdu, reply in Roman Urdu (Hindi-Urdu in Latin letters). If English, English. If mixed, mix the same way. NEVER use Urdu script.\n"
            "3. NEVER greet the user. NEVER say 'Salam', 'Hello', 'Hi', or introduce yourself. You are already in conversation.\n"
            "4. NEVER repeat your previous reply or re-ask a question the user already answered.\n"
            "5. If the user's message is short or ambiguous (e.g. 'gla khrab ha', '?', 'ok'), DO NOT ask 'what danger are you in' or generic questions. Instead, gently ask a CONCRETE follow-up about THIS message (e.g. 'kab se khrab hai gala? Kuch liya hai aaj?').\n"
            "6. NEVER give medical advice, dosages, diagnoses, or recovery protocols. NEVER moralise about substance use.\n"
            "7. Acknowledge what the user said in your own words first, THEN ask one focused follow-up question.",
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

    api = FastAPI(title="Sahara AI (Modal -> Gemini 2.5 Flash)", version="0.6.0")

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
            "backend": "gemini",
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

        messages: list[dict] = [
            {"role": "system", "content": system_prompt(language_hint, list(req.prior_summaries or []))}
        ]
        for turn in (req.history or []):
            role = turn.role if turn.role in ("user", "assistant") else "user"
            content = (turn.content or "").strip()
            if content:
                messages.append({"role": role, "content": content})
        messages.append({"role": "user", "content": text})

        try:
            reply = call_gemini(messages, max_tokens=512)
        except Exception as exc:
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
            summary = call_gemini(messages, max_tokens=320)
        except Exception as exc:
            return {"summary": "", "error": str(exc)[:200]}
        return {"summary": summary}

    return api
