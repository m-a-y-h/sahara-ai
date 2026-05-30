"""Sahara AI chat endpoint on Modal — HuggingFace Inference API backend.

This deploy is a thin proxy. The protocol layer (``sahara_ai_chat``) still
runs on Modal — system prompt construction, language detection, risk-level
parsing, response normalisation — but the actual token generation is sent to
HuggingFace's Inference API (default provider: ``featherless-ai``) instead of
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
DEFAULT_MODEL_ID = "enstazao/Qalb-1.0-8B-Instruct"
DEFAULT_PROVIDER = "featherless-ai"

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
    def text_generator(prompt: str) -> str:
        out = client.text_generation(
            prompt,
            model=model_id,
            max_new_tokens=240,
            temperature=0.15,
            top_p=0.9,
            repetition_penalty=1.08,
            do_sample=True,
            stop_sequences=STOP_SEQUENCES,
        )
        text = (out or "").strip()
        for stop in STOP_SEQUENCES:
            if text.endswith(stop):
                text = text[: -len(stop)].rstrip()
        return text

    api = FastAPI(title="Sahara AI (Modal -> HF Inference)", version="0.3.0")

    class ChatRequest(BaseModel):
        user_input: str
        language: str | None = None
        is_english: bool | None = None

    @api.get("/")
    def root() -> dict:
        return {
            "service": APP_NAME,
            "host": "modal",
            "backend": "hf-inference",
            "model": model_id,
            "provider": provider,
            "endpoints": ["/v1/chat", "/healthz"],
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
        return sahara_ai_chat(
            req.user_input,
            preferred_language=preferred,
            text_generator=text_generator,
        )

    return api
