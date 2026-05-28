"""Single-image inference path used by the FastAPI endpoint and offline tools.

Loading the model is the dominant latency cost (~1.5s cold start on CPU), so
``InferenceEngine`` is built to be instantiated once at process start and
reused across requests. It is *thread-safe for read-only inference* because
PyTorch's eval-mode forward pass is reentrant; do not call ``train()`` on the
underlying model from another thread.
"""

from __future__ import annotations

import io
import threading
from pathlib import Path
from typing import Optional, Union

import torch
from PIL import Image

from .config import EMOTION_CLASSES, MODEL_CONFIG, SCREENING_CLASSES, SCREENING_CONFIG
from .model import HybridResNetViT
from .quality_gate import QualityGateResult, QualityThresholds, run_quality_gate
from .screening import ScreeningResult, screen_emotions
from .transforms import build_eval_transform







class LensInferenceResult:
    """Aggregated outcome of the gate + model + screening pipeline."""

    def __init__(
        self,
        quality: QualityGateResult,
        screening: Optional[ScreeningResult],
        model_version: str,
    ) -> None:
        self.quality = quality
        self.screening = screening
        self.model_version = model_version

    @property
    def passed(self) -> bool:
        return self.quality.passed and self.screening is not None

    def to_dict(self) -> dict:
        return {
            "passed": self.passed,
            "model_version": self.model_version,
            "quality_gate": self.quality.to_dict(),
            "screening": self.screening.to_dict() if self.screening is not None else None,
        }







class InferenceEngine:
    """Wraps a single ``HybridResNetViT`` checkpoint for serving."""

    def __init__(
        self,
        checkpoint_path: Optional[Union[str, Path]] = None,
        device: Optional[Union[str, torch.device]] = None,
        quality_thresholds: Optional[QualityThresholds] = None,
        pretrained_fallback: bool = True,
    ) -> None:
        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.quality_thresholds = quality_thresholds
        self.transform = build_eval_transform()

        
        
        
        
        self.model = HybridResNetViT(
            config=MODEL_CONFIG,
            num_classes=len(EMOTION_CLASSES),
            pretrained=pretrained_fallback and checkpoint_path is None,
        )
        if checkpoint_path is not None:
            ckpt = torch.load(checkpoint_path, map_location=self.device)
            state = ckpt.get("model_state_dict", ckpt)
            self.model.load_state_dict(state)
            self.model_version = f"checkpoint:{Path(checkpoint_path).name}"
        else:
            self.model_version = "imagenet-init-no-finetune (smoke-test mode)"

        self.model.to(self.device)
        self.model.eval()
        self._lock = threading.Lock()  

    
    def _preprocess(self, image_bytes: bytes) -> torch.Tensor:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        tensor = self.transform(img)
        return tensor.unsqueeze(0).to(self.device)

    @torch.no_grad()
    def _predict_probs(self, x: torch.Tensor) -> dict[str, float]:
        logits = self.model(x)
        probs = torch.softmax(logits, dim=-1)[0].cpu().tolist()
        return {cls: float(p) for cls, p in zip(EMOTION_CLASSES, probs)}

    
    def predict(self, image_bytes: bytes) -> LensInferenceResult:
        """Full pipeline: quality gate → model forward → screening."""
        gate = run_quality_gate(
            image_bytes,
            thresholds=self.quality_thresholds or QualityThresholds(),
        )
        if not gate.passed:
            return LensInferenceResult(quality=gate, screening=None, model_version=self.model_version)

        with self._lock:
            assert not self.model.training, "InferenceEngine model unexpectedly in train mode"
            x = self._preprocess(image_bytes)
            raw_probs = self._predict_probs(x)

        screening = screen_emotions(raw_probs, cfg=SCREENING_CONFIG)
        return LensInferenceResult(quality=gate, screening=screening, model_version=self.model_version)







_engine: Optional[InferenceEngine] = None
_engine_lock = threading.Lock()


def get_engine(checkpoint_path: Optional[Union[str, Path]] = None) -> InferenceEngine:
    """Return a process-wide ``InferenceEngine``, building it on first call.

    The FastAPI app calls this in its startup hook. Subsequent calls return the
    same instance regardless of the checkpoint argument — pass the real path
    only on first call (or call ``reset_engine()`` first to swap checkpoints).
    """
    global _engine
    with _engine_lock:
        if _engine is None:
            _engine = InferenceEngine(checkpoint_path=checkpoint_path)
        return _engine


def reset_engine() -> None:
    """Drop the cached engine. Mainly for tests / hot-reloading checkpoints."""
    global _engine
    with _engine_lock:
        _engine = None
