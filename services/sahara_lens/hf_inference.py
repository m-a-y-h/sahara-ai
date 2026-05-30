"""Pretrained-model inference backend for Sahara Lens.

Swaps the in-house HybridResNetViT for a ready-made, Apache-2.0 facial-emotion
ViT from Hugging Face (default: ``dima806/facial_emotions_image_detection``,
~91.9% test accuracy across the full 7-class set). This removes the training
step entirely — the model already covers anger/disgust/fear, which the small
local datasets (Mendeley / IIITM) lacked.

It produces the SAME 7-class probability dict the screening layer expects, so
the ``/scan`` response and the Android client are unchanged. It also reuses the
exact same quality gate and screening logic as the in-house engine — only the
model that turns pixels into emotion probabilities is different.

Selected via env var ``SAHARA_LENS_BACKEND=hf`` (see ``inference.get_engine``).
"""

from __future__ import annotations

import io
from typing import Optional

from PIL import Image

from .config import EMOTION_CLASSES, SCREENING_CONFIG
from .inference import LensInferenceResult
from .quality_gate import QualityThresholds, run_quality_gate
from .screening import screen_emotions

# Hugging Face emotion label -> Sahara canonical class (config.EMOTION_CLASSES).
# Keys are lower-cased; several spellings are accepted so a different but
# compatible model can be dropped in without code changes.
_HF_TO_CANONICAL = {
    "angry": "anger",
    "anger": "anger",
    "disgust": "disgust",
    "disgusted": "disgust",
    "fear": "fear",
    "fearful": "fear",
    "happy": "happiness",
    "happiness": "happiness",
    "neutral": "neutral",
    "calm": "neutral",
    "sad": "sadness",
    "sadness": "sadness",
    "surprise": "surprise",
    "surprised": "surprise",
}


class HfLensEngine:
    """Drop-in replacement for ``InferenceEngine`` backed by a HF image-classifier.

    Mirrors ``InferenceEngine.predict`` so ``api.scan`` can use it interchangeably:
    quality gate -> emotion probabilities -> screening -> ``LensInferenceResult``.
    """

    def __init__(
        self,
        model_id: str = "dima806/facial_emotions_image_detection",
        device: Optional[str] = None,
        quality_thresholds: Optional[QualityThresholds] = None,
    ) -> None:
        import torch
        from transformers import AutoImageProcessor, AutoModelForImageClassification

        self._torch = torch
        self.device = torch.device(device or ("cuda" if torch.cuda.is_available() else "cpu"))
        self.quality_thresholds = quality_thresholds
        self.processor = AutoImageProcessor.from_pretrained(model_id)
        self.model = AutoModelForImageClassification.from_pretrained(model_id)
        self.model.to(self.device).eval()
        self._id2label = {int(k): v for k, v in self.model.config.id2label.items()}
        self.model_version = f"hf:{model_id}"

        # Fail fast at startup if the model emits a label we can't map onto the
        # canonical set — better a clear boot error than silent garbage scores.
        unmapped = sorted(
            {str(v).lower() for v in self._id2label.values()} - set(_HF_TO_CANONICAL)
        )
        if unmapped:
            raise ValueError(
                f"HF model '{model_id}' has unmapped labels {unmapped}; "
                f"add them to _HF_TO_CANONICAL in hf_inference.py."
            )

    def _predict_probs(self, image_bytes: bytes) -> dict[str, float]:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        inputs = self.processor(images=img, return_tensors="pt").to(self.device)
        with self._torch.no_grad():
            logits = self.model(**inputs).logits
        probs = self._torch.softmax(logits, dim=-1)[0].cpu().tolist()
        canonical = {c: 0.0 for c in EMOTION_CLASSES}
        for idx, p in enumerate(probs):
            label = str(self._id2label[idx]).lower()
            canonical[_HF_TO_CANONICAL[label]] += float(p)
        return canonical

    def predict(self, image_bytes: bytes) -> LensInferenceResult:
        """Full pipeline: quality gate -> HF model -> screening."""
        gate = run_quality_gate(
            image_bytes,
            thresholds=self.quality_thresholds or QualityThresholds(),
        )
        if not gate.passed:
            return LensInferenceResult(
                quality=gate, screening=None, model_version=self.model_version
            )
        raw_probs = self._predict_probs(image_bytes)
        screening = screen_emotions(raw_probs, cfg=SCREENING_CONFIG)
        return LensInferenceResult(
            quality=gate, screening=screening, model_version=self.model_version
        )
