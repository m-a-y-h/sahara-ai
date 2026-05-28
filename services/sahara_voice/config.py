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
from typing import Mapping






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
    """Hyperparameters for the HuBERT-based emotion classifier.

    Defaults match the teammate's snippet (HuBERT-base, 8 classes) but every
    field is overridable from ``model_config.json`` at load time. The upstream
    repo used HuBERT-large; if you fine-tune from that, set
    ``backbone="facebook/hubert-large-ls960-ft"`` and the model will adapt.
    """

    backbone: str = "facebook/hubert-base-ls960"
    num_classes: int = 8
    dropout_in: float = 0.35
    dropout_mid: float = 0.30
    dropout_out: float = 0.20
    hidden1: int = 512
    hidden2: int = 256


DEFAULT_MODEL_CONFIG = VoiceModelConfig()







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
