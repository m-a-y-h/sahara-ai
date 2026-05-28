"""Sahara Lens — facial emotion recognition for distress screening.

Detects the negative-emotion cluster (stress, sadness, fear) on South Asian
faces as a screening signal — never a diagnosis — for the SAHARA AI app.

Public surface:
    - EMOTION_CLASSES       : canonical 7-class label order
    - SCREENING_CLASSES     : downstream 4-class screening labels
    - NEG_EMOTION_GROUP     : the three negative classes the model is judged on
    - MODEL_IMAGE_SIZE      : input resolution constant
    - ScreeningResult       : screening dataclass returned by screen_emotions
    - ScreeningLevel        : enum (UNCERTAIN, NEUTRAL, ELEVATED, HIGH)
    - screen_emotions       : emotion-prob dict → ScreeningResult
    - QualityGateResult     : output of the server-side quality check
    - run_quality_gate      : run the quality gate on image bytes
    - HybridResNetViT       : model class (lazy — requires torch)

``HybridResNetViT`` is imported lazily via ``__getattr__`` so the screening
and quality-gate primitives remain importable in environments that don't have
torch installed (e.g. the unit tests that only depend on numpy + Pillow).
"""

from .config import (
    EMOTION_CLASSES,
    MODEL_IMAGE_SIZE,
    NEG_EMOTION_GROUP,
    SCREENING_CLASSES,
)
from .quality_gate import QualityGateResult, run_quality_gate
from .screening import ScreeningLevel, ScreeningResult, screen_emotions

__all__ = [
    "EMOTION_CLASSES",
    "SCREENING_CLASSES",
    "NEG_EMOTION_GROUP",
    "MODEL_IMAGE_SIZE",
    "HybridResNetViT",
    "ScreeningResult",
    "ScreeningLevel",
    "screen_emotions",
    "QualityGateResult",
    "run_quality_gate",
]


def __getattr__(name: str):
    
    
    if name == "HybridResNetViT":
        from .model import HybridResNetViT as _M
        return _M
    raise AttributeError(f"module 'sahara_lens' has no attribute {name!r}")
