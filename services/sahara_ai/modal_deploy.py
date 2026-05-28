"""Modal.com deployment for the Sahara AI ``/v1/chat`` endpoint.

Modal is the recommended host for the FYP: A10G GPUs are 24 GB VRAM (plenty
for the 4-bit quantized 8B Qalb fine-tune), cold starts amortise across the
``modal.Volume`` weight cache, and Modal bills per-second of GPU time so the
endpoint is free while no one is chatting.

Deploy
------

::

    pip install modal>=0.65
    modal token new                                       # first time only
    modal deploy sahara_ai/modal_deploy.py

Modal prints a public HTTPS URL on deploy, e.g.
``https://<user>--sahara-ai-chat-endpoint.modal.run``. Set that as
``sahara.ai.chat.url`` in the Android app's ``local.properties``.

Configuration
-------------

The model id defaults to ``enstazao/Sahara-AI-1.0-8B-Instruct`` and is
overridable via the ``SAHARA_AI_MODEL_ID`` Modal secret. The first request
downloads the weights into the persistent ``sahara-ai-weights`` Volume so
subsequent cold starts only pay for the local copy.

Notes
-----

This file is intentionally standalone — it does NOT import ``sahara_ai.app``
because ``modal.App`` builds its container image at deploy time and we want
to control exactly which Python deps land there. The protocol layer is
imported and bundled via ``image.add_local_python_source`` so the same
``sahara_ai_chat`` / ``register_sahara_ai_routes`` code paths run on Modal as
on any other host.
"""

from __future__ import annotations

import os

import modal


APP_NAME = "sahara-ai"
GPU_SPEC = "A10G"                   
TIMEOUT_SECONDS = 600               
CONTAINER_IDLE_TIMEOUT = 120        
DEFAULT_MODEL_ID = "enstazao/Sahara-AI-1.0-8B-Instruct"



weights_volume = modal.Volume.from_name("sahara-ai-weights", create_if_missing=True)
HF_CACHE_PATH = "/root/.cache/huggingface"


image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "torch==2.4.1",
        "transformers==4.45.2",
        "accelerate==0.34.2",
        "bitsandbytes==0.44.1",
        "sentencepiece==0.2.0",
        "fastapi==0.115.0",
        "pydantic==2.9.2",
        "huggingface_hub==0.25.2",
    )
    .env(
        {
            "HF_HOME": HF_CACHE_PATH,
            "TRANSFORMERS_CACHE": HF_CACHE_PATH,
            
            "TOKENIZERS_PARALLELISM": "false",
        }
    )
    
    
    .add_local_python_source("sahara_ai")
)


app = modal.App(name=APP_NAME, image=image)


@app.cls(
    gpu=GPU_SPEC,
    timeout=TIMEOUT_SECONDS,
    scaledown_window=CONTAINER_IDLE_TIMEOUT,
    volumes={HF_CACHE_PATH: weights_volume},
    
    secrets=[modal.Secret.from_name("huggingface-secret", required_keys=["HF_TOKEN"])],
)
class SaharaAIService:
    """Long-lived class so the model is loaded once per container, not per request."""

    @modal.enter()
    def load_model(self) -> None:
        import threading

        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

        model_id = os.environ.get("SAHARA_AI_MODEL_ID", DEFAULT_MODEL_ID)
        print(f"[sahara-ai] loading {model_id} onto {GPU_SPEC} …", flush=True)

        bnb = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

        self.tokenizer = AutoTokenizer.from_pretrained(model_id)
        self.model = AutoModelForCausalLM.from_pretrained(
            model_id,
            quantization_config=bnb,
            device_map={"": 0},
            torch_dtype=torch.float16,
            low_cpu_mem_usage=True,
        )
        eot_id = self.tokenizer.convert_tokens_to_ids("<|eot_id|>")
        self.terminators = [self.tokenizer.eos_token_id]
        if isinstance(eot_id, int) and eot_id >= 0:
            self.terminators.append(eot_id)

        self.lock = threading.Lock()
        print("[sahara-ai] ready.", flush=True)

    @modal.method()
    def chat(self, user_input: str, language: str | None = None) -> dict:
        """Synchronous chat call used by both the public endpoint and unit tests."""
        
        
        from sahara_ai.sahara_ai_protocol import sahara_ai_chat

        return sahara_ai_chat(
            user_input,
            tokenizer=self.tokenizer,
            model=self.model,
            terminators=self.terminators,
            device="cuda",
            preferred_language=language,
            model_lock=self.lock,
            bypass_model_for_critical=False,
        )






web_app = modal.asgi_app


@app.function(image=image, timeout=TIMEOUT_SECONDS, scaledown_window=CONTAINER_IDLE_TIMEOUT)
@modal.asgi_app(label="chat-endpoint")
def fastapi_app():
    """Mount the FastAPI app behind a public Modal URL."""
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel

    api = FastAPI(title="Sahara AI (Modal)", version="0.2.0")
    service = SaharaAIService()

    class ChatRequest(BaseModel):
        user_input: str
        language: str | None = None
        is_english: bool | None = None

    @api.get("/")
    def root() -> dict:
        return {"service": APP_NAME, "host": "modal", "gpu": GPU_SPEC, "endpoints": ["/v1/chat"]}

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
        
        
        return service.chat.remote(req.user_input, preferred)

    return api








@app.function(image=image, timeout=TIMEOUT_SECONDS, volumes={HF_CACHE_PATH: weights_volume})
def prewarm_weights() -> dict:
    """Pre-download model weights into the persistent volume."""
    from huggingface_hub import snapshot_download

    model_id = os.environ.get("SAHARA_AI_MODEL_ID", DEFAULT_MODEL_ID)
    snapshot_download(model_id, cache_dir=HF_CACHE_PATH)
    weights_volume.commit()
    return {"prewarmed": model_id}







@app.local_entrypoint()
def main() -> None:
    service = SaharaAIService()
    crisis = service.chat.remote(
        "bhai mene boht zyada aiis pii li h ab saans ni ari help",
        "roman_urdu",
    )
    print("crisis ->", crisis.get("risk_level"), crisis.get("substance_detected"))
    benign = service.chat.remote(
        "Mere doctor ne sertraline prescribe ki hai, side effects?",
        "roman_urdu",
    )
    print("prescription ->", benign.get("user_intent"))
