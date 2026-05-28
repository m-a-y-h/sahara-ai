"""FastAPI router for the Sahara Voice ``/v1/voice/analyze`` endpoint.

Mount on top of the existing Sahara AI FastAPI app:

    from fastapi import FastAPI
    from sahara_voice.api import router as voice_router

    app = FastAPI()
    app.include_router(voice_router, prefix="/v1/voice")

Endpoints:

    GET  /healthz             liveness probe (does not touch the model).
    POST /analyze             upload WAV/FLAC/OGG/MP3; returns screening JSON.
    POST /screen-from-probs   skip the model and run only the screening
                              adapter on supplied raw emotion probabilities.

The audio bytes are never written to disk and never logged; only the derived
screening JSON is returned.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Mapping

try:
    from fastapi import APIRouter, Body, File, HTTPException, UploadFile
    from pydantic import BaseModel, Field
except ImportError as e:  
    raise ImportError(
        "FastAPI is required for sahara_voice.api. Install with: pip install fastapi pydantic"
    ) from e

from .config import DEFAULT_LABELS_4CLASS, DEFAULT_LABELS_8CLASS
from .screening import screen_voice_emotions

logger = logging.getLogger("sahara_voice.api")

router = APIRouter(tags=["sahara-voice"])

MAX_UPLOAD_BYTES = 12 * 1024 * 1024     
ACCEPTED_CONTENT_TYPES = {
    "audio/wav", "audio/x-wav",
    "audio/flac", "audio/x-flac",
    "audio/ogg", "audio/x-ogg",
    "audio/mp3", "audio/mpeg", "audio/x-mpeg",
    "audio/mp4", "audio/m4a", "audio/x-m4a",
    "audio/webm",
    "application/octet-stream",          
}


class RawProbsRequest(BaseModel):
    probs: dict[str, float] = Field(
        ...,
        description=(
            "Raw voice-emotion probabilities keyed by lowercased label. Supports "
            f"the 4-class Urdu space {list(DEFAULT_LABELS_4CLASS)} or the 8-class "
            f"RAVDESS space {list(DEFAULT_LABELS_8CLASS)}; unknown labels are "
            "folded into 'neutral'."
        ),
    )


@router.get("/healthz")
def healthz() -> dict[str, Any]:
    return {
        "ok": True,
        "service": "sahara-voice",
        "labels_4class": list(DEFAULT_LABELS_4CLASS),
        "labels_8class": list(DEFAULT_LABELS_8CLASS),
    }


@router.post("/analyze")
async def analyze(file: UploadFile = File(...)) -> dict[str, Any]:
    """Run the preprocessing pipeline + model + screening on an uploaded clip."""
    ctype = (file.content_type or "").lower()
    if ctype and ctype not in ACCEPTED_CONTENT_TYPES:
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported content-type {ctype!r}.",
        )

    data = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Audio exceeds {MAX_UPLOAD_BYTES // (1024 * 1024)} MB limit.",
        )
    if not data:
        raise HTTPException(status_code=400, detail="Empty upload.")

    
    from .inference import get_engine

    engine = get_engine(
        checkpoint_path=os.environ.get("SAHARA_VOICE_CHECKPOINT"),
        model_config_path=os.environ.get("SAHARA_VOICE_MODEL_CONFIG"),
    )
    try:
        result = engine.predict(data)
    except Exception:
        logger.exception("sahara_voice.analyze inference failure")
        raise HTTPException(status_code=500, detail="Inference failed.")
    return result.to_dict()


@router.post("/screen-from-probs")
def screen_from_probs(payload: RawProbsRequest = Body(...)) -> dict[str, Any]:
    """Pure-Python screening on supplied raw emotion probabilities."""
    try:
        result = screen_voice_emotions(payload.probs)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return result.to_dict()


def build_standalone_app() -> "FastAPI":
    from fastapi import FastAPI
    app = FastAPI(title="Sahara Voice", version="0.1.0")
    app.include_router(router, prefix="/v1/voice")
    return app


app = build_standalone_app()
