"""Inference engine: bytes → screening result.

Loads the checkpoint once at process start (or on first request, depending on
the host) and exposes a thread-safe ``predict`` method. Matches the structure
of ``sahara_lens.inference.InferenceEngine`` so the FastAPI / Modal hosts
look almost identical.
"""

from __future__ import annotations

import json
import logging
import threading
from dataclasses import replace
from pathlib import Path
from typing import Any, Optional

from .config import (
    DEFAULT_LABELS_4CLASS,
    DEFAULT_LABELS_8CLASS,
    DEFAULT_MODEL_CONFIG,
    MAX_LENGTH_SAMPLES,
    SAMPLE_RATE,
    VoiceModelConfig,
    load_id2label,
    load_voice_model_config,
)
from .preprocess import preprocess_audio_bytes
from .screening import VoiceScreeningResult, screen_voice_emotions

logger = logging.getLogger("sahara_voice.inference")


class VoiceInferenceResult:
    """Aggregated outcome of preprocessing + model forward + screening."""

    def __init__(
        self,
        screening: Optional[VoiceScreeningResult],
        raw_probs: dict[str, float],
        model_version: str,
        duration_s: float,
        sample_rate: int,
        passed: bool,
        reasons: list[str],
    ) -> None:
        self.screening = screening
        self.raw_probs = raw_probs
        self.model_version = model_version
        self.duration_s = duration_s
        self.sample_rate = sample_rate
        self.passed = passed
        self.reasons = reasons

    def to_dict(self) -> dict[str, Any]:
        return {
            "passed": self.passed,
            "model_version": self.model_version,
            "reasons": list(self.reasons),
            "audio": {
                "duration_s": round(self.duration_s, 3),
                "sample_rate": self.sample_rate,
            },
            "screening": self.screening.to_dict() if self.screening else None,
            "raw_probs": {k: round(v, 4) for k, v in self.raw_probs.items()},
        }


class VoiceInferenceEngine:
    """One model + tokenizer + labels triple, reusable across requests."""

    def __init__(
        self,
        checkpoint_path: Optional[str | Path] = None,
        model_config_path: Optional[str | Path] = None,
        device: Optional[str] = None,
        model_config: Optional[VoiceModelConfig] = None,
    ) -> None:
        import torch
        from transformers import Wav2Vec2FeatureExtractor

        self.device = self._resolve_device(device)
        cfg = model_config or DEFAULT_MODEL_CONFIG
        cfg, self.id2label = self._resolve_model_metadata(model_config_path, cfg)

        self.feature_extractor = Wav2Vec2FeatureExtractor.from_pretrained(cfg.backbone)

        cfg = replace(cfg, num_classes=len(self.id2label))

        if checkpoint_path is not None:
            from .model import load_classifier_from_checkpoint
            self.model = load_classifier_from_checkpoint(
                str(checkpoint_path),
                config=cfg,
                num_classes=cfg.num_classes,
                map_location=self.device,
            )
            self.model_version = f"checkpoint:{Path(checkpoint_path).name}"
        else:
            from .model import HubertEmotionClassifier
            self.model = HubertEmotionClassifier(config=cfg, pretrained=True)
            self.model_version = "hubert-pretrained-no-finetune (smoke-test mode)"

        self.model.to(self.device).eval()
        self._lock = threading.Lock()

    
    @staticmethod
    def _resolve_device(device: Optional[str]) -> str:
        if device:
            return device
        try:
            import torch
            return "cuda" if torch.cuda.is_available() else "cpu"
        except ImportError:
            return "cpu"

    @staticmethod
    def _resolve_model_metadata(
        model_config_path: Optional[str | Path],
        cfg: VoiceModelConfig,
    ) -> tuple[VoiceModelConfig, dict[int, str]]:
        data: dict[str, Any] = {}
        if model_config_path is not None and Path(model_config_path).exists():
            data = json.loads(Path(model_config_path).read_text())
            cfg = load_voice_model_config(data, cfg)
            if "id2label" in data:
                id2label = load_id2label(data["id2label"])
                if cfg.num_classes != len(id2label):
                    raise ValueError(
                        "model_config.json num_classes does not match id2label: "
                        f"num_classes={cfg.num_classes}, labels={len(id2label)}"
                    )
                return cfg, id2label

        if cfg.num_classes == len(DEFAULT_LABELS_8CLASS):
            return cfg, {i: l for i, l in enumerate(DEFAULT_LABELS_8CLASS)}
        if cfg.num_classes == len(DEFAULT_LABELS_4CLASS):
            return cfg, {i: l for i, l in enumerate(DEFAULT_LABELS_4CLASS)}
        raise ValueError(
            f"No model_config.json supplied and num_classes={cfg.num_classes} doesn't "
            f"match a known label space (4-class Urdu or 8-class RAVDESS)."
        )

    def predict(self, audio_bytes: bytes) -> VoiceInferenceResult:
        import torch

        try:
            waveform = preprocess_audio_bytes(audio_bytes)
        except Exception as exc:
            return VoiceInferenceResult(
                screening=None,
                raw_probs={},
                model_version=self.model_version,
                duration_s=0.0,
                sample_rate=SAMPLE_RATE,
                passed=False,
                reasons=[f"preprocess failed: {type(exc).__name__}: {exc}"],
            )

        with self._lock:
            inputs = self.feature_extractor(
                waveform,
                sampling_rate=SAMPLE_RATE,
                return_tensors="pt",
                padding=False,
            )
            input_values = inputs.input_values.to(self.device)
            with torch.no_grad():
                probs = self.model.predict_probs(input_values)[0].cpu().tolist()

        raw_probs = {self.id2label[i]: float(p) for i, p in enumerate(probs)}
        screening = screen_voice_emotions(raw_probs)
        duration_s = float(waveform.size) / float(SAMPLE_RATE)

        return VoiceInferenceResult(
            screening=screening,
            raw_probs=raw_probs,
            model_version=self.model_version,
            duration_s=duration_s,
            sample_rate=SAMPLE_RATE,
            passed=True,
            reasons=["ok"],
        )







_engine: Optional[VoiceInferenceEngine] = None
_engine_lock = threading.Lock()


def get_engine(
    checkpoint_path: Optional[str] = None,
    model_config_path: Optional[str] = None,
) -> VoiceInferenceEngine:
    """Return the process-wide engine, building it on first call."""
    global _engine
    with _engine_lock:
        if _engine is None:
            _engine = VoiceInferenceEngine(
                checkpoint_path=checkpoint_path,
                model_config_path=model_config_path,
            )
        return _engine


def reset_engine() -> None:
    global _engine
    with _engine_lock:
        _engine = None
