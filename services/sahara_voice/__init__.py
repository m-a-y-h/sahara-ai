"""Sahara Voice — speech-emotion screening for distress detection.

A speech-backbone classifier that runs over a captured voice note and produces
the same 4-class screening output (NEUTRAL / STRESS / SADNESS / FEAR /
UNCERTAIN) as Sahara Lens — so the Android client can route the user to the
counselor, meditation, or emergency surface based on either modality with
identical UI logic.

The package was finalised from teammate speech-emotion experiments, including
RAVDESS-style and UrduSER-style label spaces. Both are supported via the
``id2label`` map persisted in ``model_config.json`` alongside the checkpoint.

Public surface re-exports the lightweight pieces so screening/quality tests
run without pulling in torch / librosa / noisereduce.
"""

from .config import (
    DEFAULT_LABELS_4CLASS,
    DEFAULT_LABELS_8CLASS,
    SAHARA_SCREENING_CLASSES,
    MAX_LENGTH_SAMPLES,
    SAMPLE_RATE,
)
from .screening import (
    VoiceScreeningResult,
    VoiceScreeningLevel,
    voice_emotions_to_screening,
    screen_voice_emotions,
)

__all__ = [
    "DEFAULT_LABELS_4CLASS",
    "DEFAULT_LABELS_8CLASS",
    "SAHARA_SCREENING_CLASSES",
    "MAX_LENGTH_SAMPLES",
    "SAMPLE_RATE",
    "VoiceScreeningResult",
    "VoiceScreeningLevel",
    "voice_emotions_to_screening",
    "screen_voice_emotions",
]


def __getattr__(name: str):
    
    
    
    if name == "HubertEmotionClassifier":
        from .model import HubertEmotionClassifier as _M
        return _M
    if name == "VoiceInferenceEngine":
        from .inference import VoiceInferenceEngine as _E
        return _E
    raise AttributeError(f"module 'sahara_voice' has no attribute {name!r}")
