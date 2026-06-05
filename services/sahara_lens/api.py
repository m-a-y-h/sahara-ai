"""FastAPI router exposing the Sahara Lens screening endpoint.

Mount on top of the existing Sahara AI FastAPI app:

    from fastapi import FastAPI
    from sahara_lens.api import router as lens_router

    app = FastAPI()
    app.include_router(lens_router, prefix="/v1/lens")

Endpoints:

    GET  /healthz             liveness probe — does not load the model.
    POST /scan                upload a JPEG/PNG selfie; returns screening JSON.
    POST /screen-from-probs   pass in pre-computed raw emotion probabilities
                              (for end-to-end backend testing without sending
                              a real image).

The endpoint is intentionally model-version-aware: the response JSON carries
``model_version`` so the Android client and counselor dashboard can show the
provenance of the screening signal.

Privacy notes:
    * The uploaded bytes are never written to disk and never logged. Only the
      derived screening result (a small JSON blob) is returned and may be
      written to Firestore by the client.
    * The endpoint accepts a single image per request; no batching, no
      multipart history. This is enforced so a misconfigured client cannot
      accidentally retain user photos on the server.
"""

from __future__ import annotations

import logging
import os
from typing import Optional

try:
    from fastapi import APIRouter, Body, File, HTTPException, UploadFile
    from pydantic import BaseModel, Field
except ImportError as e:  
    raise ImportError(
        "FastAPI is required for sahara_lens.api. Install with: pip install fastapi pydantic"
    ) from e

from .config import EMOTION_CLASSES, SCREENING_CLASSES
from .screening import screen_emotions

logger = logging.getLogger("sahara_lens.api")

router = APIRouter(tags=["sahara-lens"])

MAX_UPLOAD_BYTES = 8 * 1024 * 1024  
ACCEPTED_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}







class RawProbsRequest(BaseModel):
    """Payload for the ``/screen-from-probs`` debug endpoint."""

    probs: dict[str, float] = Field(
        ...,
        description=(
            "Raw 7-class emotion probabilities keyed by class name. Keys must cover the full "
            f"label set: {list(EMOTION_CLASSES)}."
        ),
    )







@router.get("/healthz")
def healthz() -> dict:
    """Liveness probe — does not touch the model so it survives bad checkpoints."""
    return {
        "ok": True,
        "service": "sahara-lens",
        "emotion_classes": list(EMOTION_CLASSES),
        "screening_classes": list(SCREENING_CLASSES),
    }







@router.post("/scan")
async def scan(file: UploadFile = File(...)) -> dict:
    """Run the quality gate, model, and screening layer on an uploaded image."""
    if file.content_type and file.content_type not in ACCEPTED_CONTENT_TYPES:
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported content-type '{file.content_type}'. Accepted: {sorted(ACCEPTED_CONTENT_TYPES)}.",
        )

    
    data = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image exceeds {MAX_UPLOAD_BYTES // (1024 * 1024)} MB limit.",
        )
    if len(data) == 0:
        raise HTTPException(status_code=400, detail="Empty upload.")

    
    from .inference import get_engine

    engine = get_engine(checkpoint_path=os.environ.get("SAHARA_LENS_CHECKPOINT"))
    try:
        result = engine.predict(data)
    except Exception:
        
        logger.exception("sahara_lens.scan inference failure")
        raise HTTPException(status_code=500, detail="Inference failed.")

    return result.to_dict()







@router.post("/screen-from-probs")
def screen_from_probs(payload: RawProbsRequest = Body(...)) -> dict:
    """Run the screening layer on pre-computed emotion probabilities.

    Exposes the pure-Python screening logic so the Android team can validate
    the contract without needing to send real images at every iteration of
    UI work.
    """
    missing = set(EMOTION_CLASSES) - set(payload.probs)
    if missing:
        raise HTTPException(
            status_code=400,
            detail=f"Missing emotion probabilities for: {sorted(missing)}.",
        )
    try:
        result = screen_emotions(payload.probs)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return result.to_dict()







def build_standalone_app() -> "FastAPI":
    """Convenience constructor: ``uvicorn sahara_lens.api:app`` deploys just lens.

    Other FastAPI hosts can include this ``router`` directly if they want Lens
    to share a process with another service.
    """
    from fastapi import FastAPI

    app = FastAPI(title="Sahara Lens", version="0.1.0")
    app.include_router(router, prefix="/v1/lens")
    return app



app = build_standalone_app()
