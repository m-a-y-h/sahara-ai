"""Configuration constants for the Sahara Voice pipeline.

Two real-world label spaces are supported out of the box:

    * **4-class Urdu (SEMOUR+)** — anger, happiness, neutral, sadness. This is
      what the upstream `Emotion-detection-from-audio-in-urdu` repo trained on.
      No `fear` class is present; the screening adapter handles that gracefully.
    * **8-class RAVDESS** — anger, calm, disgust, fearful, happy, neutral, sad,
      surprised. Matches the teammate's published checkpoint snippet.

The actual classes a checkpoint was trained on are read at load time from a
sibling ``model_config.json`` (the same convention the teammate's snippet
uses), so a single binary works for both — just point ``CHECKPOINT_PATH`` at
the right `.pt`.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping






SAMPLE_RATE: int = 16_000               
MAX_LENGTH_SAMPLES: int = SAMPLE_RATE * 6   
                                            
                                            









DEFAULT_LABELS_4CLASS: tuple[str, ...] = ("anger", "happiness", "neutral", "sadness")


DEFAULT_LABELS_8CLASS: tuple[str, ...] = (
    "anger",
    "calm",
    "disgust",
    "fearful",
    "happy",
    "neutral",
    "sad",
    "surprised",
)





SAHARA_SCREENING_CLASSES: tuple[str, ...] = ("neutral", "stress", "sadness", "fear")






VOICE_LABEL_TO_SCREENING: dict[str, str] = {
    
    "anger":     "stress",
    "happiness": "neutral",
    "neutral":   "neutral",
    "sadness":   "sadness",
    
    "calm":      "neutral",
    "disgust":   "stress",
    "fearful":   "fear",
    "happy":     "neutral",
    "sad":       "sadness",
    "surprised": "neutral",
    
    "fear":      "fear",
    "angry":     "stress",
}




STRESS_PROXY_WEIGHT: float = 0.5



MIN_TOP_CLASS_PROB: float = 0.35
ELEVATED_THRESHOLD: float = 0.45
HIGH_THRESHOLD: float = 0.65







@dataclass(frozen=True)
class VoiceModelConfig:
    """Hyperparameters for the speech-backbone emotion classifier.

    The default backbone stays small for smoke tests. Production checkpoints
    should provide ``backbone`` in ``model_config.json``; the current Urdu
    fine-tune uses ``facebook/wav2vec2-xls-r-300m``.
    """

    backbone: str = "facebook/hubert-base-ls960"
    num_classes: int = 8
    dropout_in: float = 0.35
    dropout_mid: float = 0.30
    dropout_out: float = 0.20
    hidden1: int = 256
    hidden2: int = 128


DEFAULT_MODEL_CONFIG = VoiceModelConfig()


def load_voice_model_config(
    raw: Mapping[str, Any],
    base: VoiceModelConfig = DEFAULT_MODEL_CONFIG,
) -> VoiceModelConfig:
    """Load backbone/head settings from ``model_config.json`` data."""
    raw_cfg = raw.get("model_config") or raw.get("config") or raw
    if not isinstance(raw_cfg, Mapping):
        raw_cfg = raw
    default_num_classes = base.num_classes
    raw_labels = raw.get("id2label")
    if isinstance(raw_labels, Mapping):
        default_num_classes = len(raw_labels)

    def _str_field(*keys: str, default: str) -> str:
        for key in keys:
            value = raw_cfg.get(key)
            if value is None and raw_cfg is not raw:
                value = raw.get(key)
            if value is not None:
                return str(value)
        return default

    def _int_field(key: str, default: int) -> int:
        value = raw_cfg.get(key)
        if value is None and raw_cfg is not raw:
            value = raw.get(key)
        if value is None:
            return default
        return int(value)

    def _float_field(key: str, default: float) -> float:
        value = raw_cfg.get(key)
        if value is None and raw_cfg is not raw:
            value = raw.get(key)
        if value is None:
            return default
        return float(value)

    return VoiceModelConfig(
        backbone=_str_field(
            "backbone",
            "model_name",
            "model_name_or_path",
            "hubert_model",
            "hubert_model_name",
            default=base.backbone,
        ),
        num_classes=_int_field("num_classes", default_num_classes),
        dropout_in=_float_field("dropout_in", base.dropout_in),
        dropout_mid=_float_field("dropout_mid", base.dropout_mid),
        dropout_out=_float_field("dropout_out", base.dropout_out),
        hidden1=_int_field("hidden1", base.hidden1),
        hidden2=_int_field("hidden2", base.hidden2),
    )







def load_id2label(raw: Mapping[str | int, str]) -> dict[int, str]:
    """Normalise an ``id2label`` map loaded from JSON (keys may be strings).

    Also lower-cases the labels because the teammate's snippet keeps them
    capitalised and the screening adapter expects lowercase keys.
    """
    out: dict[int, str] = {}
    for k, v in raw.items():
        try:
            idx = int(k)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"id2label keys must be int-like; got {k!r}") from exc
        out[idx] = str(v).strip().lower()
    if not out:
        raise ValueError("id2label is empty")
    return out
