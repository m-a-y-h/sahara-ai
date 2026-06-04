"""Sahara AI chat endpoint on Modal — HuggingFace Inference API backend.

This deploy is a thin proxy. The protocol layer (``sahara_ai_chat``) still
runs on Modal — system prompt construction, language detection, risk-level
parsing, response normalisation — but the actual token generation is sent to
HuggingFace's Inference API (auto-routed via the ``:fastest`` suffix so HF
picks the live provider and fails over if one rate-limits) instead of
loading the 8B model onto a Modal GPU. That means:

* No A10G / no quantization plumbing / no bitsandbytes.
* Cold start is milliseconds (the function just instantiates an HTTP client).
* Cost moves from per-second GPU time on Modal to per-token billing on HF.

Setup (one-time):
    modal secret create huggingface-secret HF_TOKEN=<your hf token>

Deploy — MUST run from the ``services/`` directory so ``add_local_python_source
("sahara_ai")`` can import the local package, otherwise Modal errors with
"sahara_ai has no spec - might not be installed?":
    cd services && modal deploy sahara_ai/modal_deploy.py

Modal prints an https URL. Set it in the Android app's ``local.properties``:
    sahara.ai.chat.url=<that url>/v1/chat

The model id and provider are env-overridable so you can repoint without a
redeploy: set SAHARA_AI_MODEL_ID and/or SAHARA_AI_PROVIDER on the Modal Secret
(or as ``.env`` entries on the image).
"""

import os

import modal

APP_NAME = "sahara-ai"
# `:fastest` is HF's auto-routing suffix. Combined with provider="auto"
# below, the InferenceClient asks HF's router to pick the fastest live
# provider for the model and transparently fail over if the first one
# rate-limits or 5xxes. Featherless-ai is currently the only listed
# provider for this model, but the auto path still survives a transient
# featherless outage (e.g. the per-IP free-tier rate cap that knocked
# out the second turn during testing) where a hard-coded provider
# wouldn't. Override via SAHARA_AI_MODEL_ID / SAHARA_AI_PROVIDER env
# vars on the Modal Secret if needed.
DEFAULT_MODEL_ID = "enstazao/Qalb-1.0-8B-Instruct:fastest"
DEFAULT_PROVIDER = "auto"

# Llama-3.1 chat template uses <|eot_id|> to end the assistant turn. We pass it
# as a stop sequence so the remote provider trims cleanly; the protocol's
# normaliser does the final cleanup of any trailing template noise.
STOP_SEQUENCES = ["<|eot_id|>"]

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "fastapi==0.115.0",
        "pydantic==2.9.2",
        # 0.27+ exposes provider-routed text_generation and stop_sequences cleanly.
        "huggingface_hub>=0.27.0",
    )
    .env({"TOKENIZERS_PARALLELISM": "false"})
    .add_local_python_source("sahara_ai")
)

app = modal.App(name=APP_NAME, image=image)


@app.function(
    timeout=120,
    scaledown_window=300,
    # The secret only needs HF_TOKEN. Optional: also include
    # SAHARA_AI_MODEL_ID and SAHARA_AI_PROVIDER to repoint without redeploying.
    secrets=[modal.Secret.from_name("huggingface-secret", required_keys=["HF_TOKEN"])],
)
@modal.asgi_app(label="chat-endpoint")
def fastapi_app():
    from fastapi import FastAPI, HTTPException
    from huggingface_hub import InferenceClient
    from pydantic import BaseModel

    from sahara_ai.sahara_ai_protocol import sahara_ai_chat

    model_id = os.environ.get("SAHARA_AI_MODEL_ID", DEFAULT_MODEL_ID)
    provider = os.environ.get("SAHARA_AI_PROVIDER", DEFAULT_PROVIDER)
    client = InferenceClient(provider=provider, api_key=os.environ["HF_TOKEN"])

    # text_generator(prompt) -> str. Parameters mirror the local generation
    # path in sahara_ai_protocol.generate_with_sahara_ai so the prompt
    # engineering tuned for the local model still applies on the remote one.
    def text_generator(prompt: str, max_new_tokens: int = 512) -> str:
        # Sampling tuned to keep Qalb (Llama-3.1-8B fine-tune) out of the
        # "Aaj sharam hain? Aaj rata hain? Aaj subah hain?" repetition
        # collapse it falls into at low temperature. Previous values
        # (T=0.15, rep_pen=1.08) reliably produced a sensible opening then
        # latched onto a 4-5 token cycle until max_new_tokens ran out.
        #   - temperature 0.6: warm enough to break out of token-cycles
        #     while staying coherent for the bilingual safety context.
        #   - repetition_penalty 1.2: typical for Llama-3.1 chat tunes;
        #     1.08 was too gentle to interrupt the loop once it started.
        #   - top_p 0.95: slight relax so the sampler doesn't get
        #     monomaniacal on the top-of-distribution token.
        kwargs = dict(
            prompt=prompt,
            model=model_id,
            max_new_tokens=max_new_tokens,
            temperature=0.6,
            top_p=0.95,
            repetition_penalty=1.20,
            do_sample=True,
            stop_sequences=STOP_SEQUENCES,
        )
        # One automatic retry on transient provider failures (rate-limit
        # / 5xx). Featherless's free tier reliably 429s on the second back-
        # to-back request in our testing, which had been surfacing as the
        # in-app "Main yahin hoon..." local-fallback message every other
        # turn. A short backoff is usually enough for the provider to
        # let us through; with provider="auto" the second attempt may
        # even hop to a different live provider.
        import time
        last_exc: Exception | None = None
        for attempt in range(2):
            try:
                out = client.text_generation(**kwargs)
                text = (out or "").strip()
                for stop in STOP_SEQUENCES:
                    if text.endswith(stop):
                        text = text[: -len(stop)].rstrip()
                if text:
                    return text
            except Exception as exc:
                last_exc = exc
                if attempt == 0:
                    time.sleep(1.2)
                    continue
                raise
        # Empty text and no exception — bubble up so the caller's
        # normalize_model_response can fall back to canned guidance.
        raise RuntimeError("Qalb returned an empty completion after retry")

    api = FastAPI(title="Sahara AI (Modal -> HF Inference)", version="0.4.0")

    class HistoryTurn(BaseModel):
        role: str            # "user" or "assistant"
        content: str
        timestamp_ms: int | None = None

    class ChatRequest(BaseModel):
        user_input: str
        language: str | None = None
        is_english: bool | None = None
        # Unsummarised live history of the current chat session, oldest-
        # first. Does NOT include `user_input`; we add it server-side.
        history: list[HistoryTurn] | None = None
        # Already-collapsed batch summaries (oldest-first). Prepended to
        # the prompt as "Earlier context" so continuity survives past the
        # live-history window.
        prior_summaries: list[str] | None = None

    class SummarizeRequest(BaseModel):
        # Full 16-message batch (8 user + 8 assistant) to compress.
        messages: list[HistoryTurn]
        language: str | None = None

    @api.get("/")
    def root() -> dict:
        return {
            "service": APP_NAME,
            "host": "modal",
            "backend": "hf-inference",
            "model": model_id,
            "provider": provider,
            "endpoints": ["/v1/chat", "/v1/summarize", "/healthz"],
        }

    @api.get("/healthz")
    def healthz() -> dict:
        return {"ok": True}

    @api.post("/v1/chat")
    def chat(req: ChatRequest) -> dict:
        if not req.user_input or not req.user_input.strip():
            raise HTTPException(status_code=400, detail="user_input must not be empty.")
        preferred = req.language
        if req.is_english is True and preferred is None:
            preferred = "english"
        history = [
            {"role": t.role, "content": t.content, "timestamp_ms": t.timestamp_ms}
            for t in (req.history or [])
        ]
        prior_summaries = list(req.prior_summaries or [])
        return sahara_ai_chat(
            req.user_input,
            preferred_language=preferred,
            text_generator=text_generator,
            history=history,
            prior_summaries=prior_summaries,
        )

    @api.post("/v1/summarize")
    def summarize(req: SummarizeRequest) -> dict:
        # We don't import sahara_ai_chat's full protocol for summarisation
        # — it just needs a faithful compression. Build the prompt inline.
        if not req.messages:
            raise HTTPException(status_code=400, detail="messages must not be empty.")
        language_hint = (req.language or "").strip().lower()
        transcript_lines = []
        for t in req.messages:
            speaker = "User" if t.role == "user" else "Assistant"
            transcript_lines.append(f"{speaker}: {t.content}")
        transcript = "\n".join(transcript_lines)
        instruction = (
            "You are helping the Sahara mental-health app keep continuity across "
            "long conversations. Summarise the following exchange in 3-5 short "
            "sentences. Preserve: any substance the user mentioned (drug name, "
            "frequency, context), risk signals (suicidal ideation, withdrawal, "
            "fainting, self-harm), the assistant's last guidance, and the user's "
            "current emotional state. Use the same language the user was using "
            f"({language_hint or 'auto-detect'}). Do NOT add new advice or extra "
            "framing — just the compressed factual summary."
        )
        # Use Llama-3.1 chat template manually for the summariser.
        prompt = (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            f"{instruction}<|eot_id|>"
            "<|start_header_id|>user<|end_header_id|>\n\n"
            f"{transcript}<|eot_id|>"
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
        )
        summary = text_generator(prompt, max_new_tokens=320).strip()
        return {"summary": summary}

    return api
