from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Mapping, Sequence

from .config import (
    ELEVATED_THRESHOLD,
    HIGH_THRESHOLD,
    MIN_TOP_CLASS_PROB,
    SAHARA_SCREENING_CLASSES,
    STRESS_PROXY_WEIGHT,
    VOICE_LABEL_TO_SCREENING,
)


class VoiceScreeningLevel(str, Enum):

    UNCERTAIN = "uncertain"
    NEUTRAL = "neutral"
    ELEVATED = "elevated"
    HIGH = "high"


@dataclass
class VoiceScreeningResult:

    level: VoiceScreeningLevel
    distress_score: float
    screening_probs: dict[str, float]
    raw_probs: dict[str, float]
    top_screening_class: str
    top_screening_prob: float
    reasons: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "level": self.level.value,
            "distress_score": round(self.distress_score, 4),
            "screening_probs": {k: round(v, 4) for k, v in self.screening_probs.items()},
            "raw_probs": {k: round(v, 4) for k, v in self.raw_probs.items()},
            "top_screening_class": self.top_screening_class,
            "top_screening_prob": round(self.top_screening_prob, 4),
            "reasons": list(self.reasons),
        }


def _normalise_raw(probs: Mapping[str, float] | Sequence[float],
                   id2label: Mapping[int, str] | None) -> dict[str, float]:
    if isinstance(probs, Mapping):
        return {str(k).strip().lower(): float(v) for k, v in probs.items()}
    if id2label is None:
        raise ValueError("Sequence-form probs require an id2label mapping.")
    seq = list(probs)
    if len(seq) != len(id2label):
        raise ValueError(
            f"Got {len(seq)} probabilities but id2label has {len(id2label)} labels."
        )
    return {str(id2label[i]).strip().lower(): float(p) for i, p in enumerate(seq)}


def voice_emotions_to_screening(
    raw: Mapping[str, float] | Sequence[float],
    id2label: Mapping[int, str] | None = None,
    *,
    stress_proxy_weight: float = STRESS_PROXY_WEIGHT,
) -> dict[str, float]:
    raw_lc = _normalise_raw(raw, id2label)

    aggregated: dict[str, float] = {c: 0.0 for c in SAHARA_SCREENING_CLASSES}
    stress_proxy = 0.0
    stress_direct = 0.0
    for label, prob in raw_lc.items():
        bucket = VOICE_LABEL_TO_SCREENING.get(label)
        if bucket is None:
            aggregated["neutral"] += float(prob)
            continue
        if bucket == "stress" and label in {"anger", "angry", "disgust"}:
            stress_proxy = max(stress_proxy, float(prob))
        elif bucket == "stress":
            stress_direct += float(prob)
        else:
            aggregated[bucket] += float(prob)
    aggregated["stress"] = stress_direct + stress_proxy * stress_proxy_weight

    total = sum(aggregated.values())
    if total <= 0:
        return {c: 1.0 / len(SAHARA_SCREENING_CLASSES) for c in SAHARA_SCREENING_CLASSES}
    return {k: v / total for k, v in aggregated.items()}


def screen_voice_emotions(
    raw: Mapping[str, float] | Sequence[float],
    id2label: Mapping[int, str] | None = None,
) -> VoiceScreeningResult:
    raw_lc = _normalise_raw(raw, id2label)
    screening = voice_emotions_to_screening(raw_lc)

    distress_score = screening["stress"] + screening["sadness"] + screening["fear"]
    top_class = max(screening, key=screening.get)
    top_prob = screening[top_class]

    reasons: list[str] = []
    if top_prob < MIN_TOP_CLASS_PROB:
        reasons.append(
            f"top-class probability {top_prob:.2f} below confidence floor "
            f"{MIN_TOP_CLASS_PROB:.2f}"
        )
        level = VoiceScreeningLevel.UNCERTAIN
    elif distress_score >= HIGH_THRESHOLD:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} ≥ high threshold "
            f"{HIGH_THRESHOLD:.2f}"
        )
        level = VoiceScreeningLevel.HIGH
    elif distress_score >= ELEVATED_THRESHOLD:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} ≥ elevated threshold "
            f"{ELEVATED_THRESHOLD:.2f}"
        )
        level = VoiceScreeningLevel.ELEVATED
    else:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} below elevated threshold "
            f"{ELEVATED_THRESHOLD:.2f}"
        )
        level = VoiceScreeningLevel.NEUTRAL

    if top_class != "neutral" and level == VoiceScreeningLevel.NEUTRAL:
        reasons.append(f"dominant non-neutral class is '{top_class}' ({top_prob:.2f})")

    return VoiceScreeningResult(
        level=level,
        distress_score=distress_score,
        screening_probs=screening,
        raw_probs=raw_lc,
        top_screening_class=top_class,
        top_screening_prob=top_prob,
        reasons=reasons,
    )
