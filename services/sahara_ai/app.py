from __future__ import annotations

import logging
import os
import threading
from typing import Any, Optional

from fastapi import FastAPI
from pydantic import BaseModel

from .sahara_ai_protocol import (
    register_sahara_ai_routes,
    sahara_ai_chat,
)


logger = logging.getLogger("sahara_ai.app")


class ModelHandles:

    def __init__(self) -> None:
        self.tokenizer: Any = None
        self.model: Any = None
        self.terminators: Any = None
        self.lock = threading.Lock()
        self.error: Optional[str] = None
        self._attempted = False

    def is_ready(self) -> bool:
        return self.tokenizer is not None and self.model is not None

    def status(self) -> dict[str, Any]:
        return {
            "loaded": self.is_ready(),
            "attempted": self._attempted,
            "error": self.error,
            "model_id": os.environ.get("SAHARA_AI_MODEL_ID"),
            "model_local": os.environ.get("SAHARA_AI_MODEL_LOCAL"),
            "device": _resolve_device(),
        }


HANDLES = ModelHandles()


def _resolve_device() -> str:
    explicit = os.environ.get("SAHARA_AI_DEVICE")
    if explicit:
        return explicit
    try:
        import torch

        return "cuda" if torch.cuda.is_available() else "cpu"
    except ImportError:
        return "cpu"


def _truthy(value: Optional[str], default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _load_model() -> None:
    model_id = os.environ.get("SAHARA_AI_MODEL_ID")
    model_local = os.environ.get("SAHARA_AI_MODEL_LOCAL")
    source = model_local or model_id
    if not source:
        HANDLES.error = (
            "no SAHARA_AI_MODEL_ID or SAHARA_AI_MODEL_LOCAL set — running in "
            "deterministic-only mode (safety layer still works)"
        )
        logger.warning(HANDLES.error)
        return

    try:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
    except ImportError as exc:
        HANDLES.error = f"transformers / torch not installed: {exc}"
        logger.exception(HANDLES.error)
        return

    device = _resolve_device()
    use_4bit = _truthy(os.environ.get("SAHARA_AI_LOAD_IN_4BIT"), default=device == "cuda")

    quant_config = None
    if use_4bit and device == "cuda":
        try:
            from transformers import BitsAndBytesConfig

            quant_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_compute_dtype=torch.bfloat16,
                bnb_4bit_use_double_quant=True,
            )
        except ImportError:
            logger.warning("bitsandbytes not available; loading in full precision instead.")

    logger.info(f"loading Sahara AI model from {source!r} onto {device!r}")
    try:
        tokenizer = AutoTokenizer.from_pretrained(source)
        load_kwargs: dict[str, Any] = {"low_cpu_mem_usage": True}
        if device == "cuda":
            load_kwargs["device_map"] = {"": 0}
            load_kwargs["torch_dtype"] = torch.float16
        if quant_config is not None:
            load_kwargs["quantization_config"] = quant_config
        model = AutoModelForCausalLM.from_pretrained(source, **load_kwargs)
        eot_id = tokenizer.convert_tokens_to_ids("<|eot_id|>")
        terminators = [tokenizer.eos_token_id]
        if isinstance(eot_id, int) and eot_id >= 0:
            terminators.append(eot_id)

        HANDLES.tokenizer = tokenizer
        HANDLES.model = model
        HANDLES.terminators = terminators
        HANDLES.error = None
        logger.info("Sahara AI model load complete.")
    except Exception as exc:
        HANDLES.error = f"model load failed: {type(exc).__name__}: {exc}"
        logger.exception(HANDLES.error)


def ensure_model_loaded() -> None:
    if HANDLES.is_ready() or HANDLES._attempted:
        return
    with HANDLES.lock:
        if HANDLES.is_ready() or HANDLES._attempted:
            return
        HANDLES._attempted = True
        _load_model()


app = FastAPI(
    title="Sahara AI",
    description=(
        "Harm-reduction chat layer for Pakistani youth dealing with non-prescribed "
        "drug use. The deterministic safety parser always runs in front of the LLM."
    ),
    version="0.2.0",
)


class ChatRequest(BaseModel):
    user_input: str
    language: Optional[str] = None
    is_english: Optional[bool] = None


@app.on_event("startup")
def on_startup() -> None:
    threading.Thread(target=ensure_model_loaded, daemon=True).start()


@app.get("/")
def root() -> dict[str, Any]:
    return {
        "service": "sahara-ai",
        "version": app.version,
        "model": HANDLES.status(),
        "endpoints": ["/v1/chat", "/healthz", "/readyz"],
    }


@app.get("/healthz")
def healthz() -> dict[str, Any]:
    return {"ok": True, "service": "sahara-ai"}


@app.get("/readyz")
def readyz() -> dict[str, Any]:
    status = HANDLES.status()
    status["mode"] = "model+safety" if status["loaded"] else "safety-only"
    return status


@app.post("/v1/chat")
def chat(payload: ChatRequest) -> dict[str, Any]:
    ensure_model_loaded()

    preferred_language = payload.language
    if payload.is_english is True and preferred_language is None:
        preferred_language = "english"

    bypass_for_critical = _truthy(os.environ.get("SAHARA_AI_BYPASS_MODEL_FOR_CRITICAL"))

    return sahara_ai_chat(
        payload.user_input,
        tokenizer=HANDLES.tokenizer,
        model=HANDLES.model,
        terminators=HANDLES.terminators,
        device=_resolve_device(),
        preferred_language=preferred_language,
        model_lock=HANDLES.lock,
        bypass_model_for_critical=bypass_for_critical,
    )


__all__ = ["app", "ensure_model_loaded", "HANDLES", "register_sahara_ai_routes"]
