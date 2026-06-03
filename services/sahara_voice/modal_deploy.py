"""Modal.com A10G deployment for the Sahara Voice ``/v1/voice/analyze`` endpoint.

Designed for the same Modal account as ``sahara_ai.modal_deploy`` so both
endpoints share token + secrets. Deploy:

::

    modal deploy sahara_voice/modal_deploy.py

Pre-cache weights:

::

    modal run sahara_voice/modal_deploy.py::prewarm_weights

Set the Android ``local.properties`` ``sahara.voice.analyze.url`` to the URL
Modal prints.

HuBERT-base is ~95M params (~190 MB) so the A10G is overkill for inference
latency but cheap enough that we get the same scale-to-zero behaviour the
chat endpoint enjoys. If the production traffic is tiny, swap ``gpu="A10G"``
for ``gpu="T4"`` (or remove ``gpu`` entirely — CPU works for HuBERT-base at
~3 s per request).
"""

# NB: deliberately NO `from __future__ import annotations` here.
# FastAPI inspects parameter annotations at decorator time; when annotations
# are lazy strings (PEP 563) and the symbols (UploadFile, File) are imported
# inside the endpoint factory rather than at module level, FastAPI can't
# resolve the ForwardRef and crash-loops with "Invalid args for response
# field! ... ForwardRef('UploadFile')". Resolved types fix it cleanly.

import os

import modal


APP_NAME = "sahara-voice"
GPU_SPEC = "A10G"
TIMEOUT_SECONDS = 300
CONTAINER_IDLE_TIMEOUT = 120

weights_volume = modal.Volume.from_name("sahara-voice-weights", create_if_missing=True)
HF_CACHE_PATH = "/root/.cache/huggingface"


image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("ffmpeg", "libsndfile1")
    .pip_install(
        "torch==2.4.1",
        "transformers==4.45.2",
        "accelerate==0.34.2",
        "soundfile==0.12.1",
        "librosa==0.10.2",
        "noisereduce==3.0.3",
        "numpy>=1.26",
        "fastapi==0.115.0",
        "pydantic==2.9.2",
        "python-multipart==0.0.9",
    )
    .env({
        "HF_HOME": HF_CACHE_PATH,
        "TRANSFORMERS_CACHE": HF_CACHE_PATH,
        "TOKENIZERS_PARALLELISM": "false",
        # Tell the inference engine where the fine-tuned checkpoint and
        # accompanying id2label config live inside the sahara-voice-weights
        # volume (mounted at HF_CACHE_PATH below). Upload commands:
        #
        #   modal volume put sahara-voice-weights ./best.pt          /checkpoints/best.pt
        #   modal volume put sahara-voice-weights ./model_config.json /checkpoints/model_config.json
        #
        # Both files live in the same /checkpoints subdir so HF snapshot
        # downloads (which also use this volume) don't collide.
        "SAHARA_VOICE_CHECKPOINT":   f"{HF_CACHE_PATH}/checkpoints/best.pt",
        "SAHARA_VOICE_MODEL_CONFIG": f"{HF_CACHE_PATH}/checkpoints/model_config.json",
    })
    .add_local_python_source("sahara_voice")
)


app = modal.App(name=APP_NAME, image=image)


@app.cls(
    gpu=GPU_SPEC,
    timeout=TIMEOUT_SECONDS,
    scaledown_window=CONTAINER_IDLE_TIMEOUT,
    volumes={HF_CACHE_PATH: weights_volume},
    secrets=[modal.Secret.from_name("huggingface-secret", required_keys=["HF_TOKEN"])],
)
class SaharaVoiceService:
    """Long-lived class so HuBERT is loaded once per container, not per request."""

    @modal.enter()
    def load_model(self) -> None:
        from sahara_voice.inference import get_engine

        checkpoint = os.environ.get("SAHARA_VOICE_CHECKPOINT")
        model_config = os.environ.get("SAHARA_VOICE_MODEL_CONFIG")
        print(f"[sahara-voice] loading checkpoint={checkpoint!r} on {GPU_SPEC}", flush=True)
        self.engine = get_engine(
            checkpoint_path=checkpoint,
            model_config_path=model_config,
        )
        print("[sahara-voice] ready.", flush=True)

    @modal.method()
    def analyze(self, audio_bytes: bytes) -> dict:
        return self.engine.predict(audio_bytes).to_dict()


@app.function(image=image, timeout=TIMEOUT_SECONDS, scaledown_window=CONTAINER_IDLE_TIMEOUT)
@modal.asgi_app(label="voice-endpoint")
def fastapi_app():
    from fastapi import FastAPI, File, HTTPException, UploadFile
    from sahara_voice.api import MAX_UPLOAD_BYTES, ACCEPTED_CONTENT_TYPES

    api = FastAPI(title="Sahara Voice (Modal)", version="0.1.0")
    service = SaharaVoiceService()

    @api.get("/")
    def root() -> dict:
        return {"service": APP_NAME, "host": "modal", "gpu": GPU_SPEC, "endpoints": ["/v1/voice/analyze"]}

    @api.get("/healthz")
    def healthz() -> dict:
        return {"ok": True}

    @api.post("/v1/voice/analyze")
    async def analyze(file: UploadFile = File(...)) -> dict:
        ctype = (file.content_type or "").lower()
        if ctype and ctype not in ACCEPTED_CONTENT_TYPES:
            raise HTTPException(status_code=415, detail=f"Unsupported content-type {ctype!r}.")
        data = await file.read(MAX_UPLOAD_BYTES + 1)
        if len(data) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="Audio too large.")
        if not data:
            raise HTTPException(status_code=400, detail="Empty upload.")
        return service.analyze.remote(data)

    return api


@app.function(image=image, timeout=TIMEOUT_SECONDS, volumes={HF_CACHE_PATH: weights_volume})
def prewarm_weights() -> dict:
    """Pre-download HuBERT weights into the persistent volume."""
    from huggingface_hub import snapshot_download
    from sahara_voice.config import DEFAULT_MODEL_CONFIG

    backbone = os.environ.get("SAHARA_VOICE_BACKBONE", DEFAULT_MODEL_CONFIG.backbone)
    snapshot_download(backbone, cache_dir=HF_CACHE_PATH)
    weights_volume.commit()
    return {"prewarmed": backbone}


@app.local_entrypoint()
def main() -> None:
    print("Deployed. Use `modal serve` or `modal deploy` to get a public URL.")
