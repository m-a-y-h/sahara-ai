from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Mapping

from .config import EMOTION_CLASSES, SCREENING_CLASSES, SCREENING_CONFIG, ScreeningConfig


class ScreeningLevel(str, Enum):

    UNCERTAIN = "uncertain"
    NEUTRAL = "neutral"
    ELEVATED = "elevated"
    HIGH = "high"


@dataclass
class ScreeningResult:

    level: ScreeningLevel
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


def _to_dict(probs) -> dict[str, float]:
    if isinstance(probs, Mapping):
        out = {k: float(v) for k, v in probs.items()}
    else:
        seq = list(probs)
        if len(seq) != len(EMOTION_CLASSES):
            raise ValueError(
                f"Expected {len(EMOTION_CLASSES)} probabilities ordered as "
                f"{list(EMOTION_CLASSES)}; got {len(seq)}."
            )
        out = {cls: float(p) for cls, p in zip(EMOTION_CLASSES, seq)}
    missing = set(EMOTION_CLASSES) - set(out)
    if missing:
        raise ValueError(f"Missing class probabilities for: {sorted(missing)}")
    return out


def emotion_to_screening(raw: dict[str, float], cfg: ScreeningConfig = SCREENING_CONFIG) -> dict[str, float]:
    stress_raw = max(raw["anger"], raw["disgust"]) * cfg.stress_proxy_weight
    neutral_raw = raw["neutral"] + raw["happiness"] + raw["surprise"]
    sadness_raw = raw["sadness"]
    fear_raw = raw["fear"]

    unnormalised = {
        "neutral": neutral_raw,
        "stress": stress_raw,
        "sadness": sadness_raw,
        "fear": fear_raw,
    }
    total = sum(unnormalised.values())
    if total <= 0:
        return {k: 1.0 / len(SCREENING_CLASSES) for k in SCREENING_CLASSES}
    return {k: v / total for k, v in unnormalised.items()}


def screen_emotions(probs, cfg: ScreeningConfig = SCREENING_CONFIG) -> ScreeningResult:
    raw = _to_dict(probs)
    screening = emotion_to_screening(raw, cfg=cfg)

    distress_score = screening["stress"] + screening["sadness"] + screening["fear"]
    top_class = max(screening, key=screening.get)
    top_prob = screening[top_class]

    reasons: list[str] = []
    if top_prob < cfg.min_top_class_prob:
        reasons.append(
            f"top-class probability {top_prob:.2f} below confidence floor "
            f"{cfg.min_top_class_prob:.2f}"
        )
        level = ScreeningLevel.UNCERTAIN
    elif distress_score >= cfg.high_threshold:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} ≥ high threshold "
            f"{cfg.high_threshold:.2f}"
        )
        level = ScreeningLevel.HIGH
    elif distress_score >= cfg.elevated_threshold:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} ≥ elevated threshold "
            f"{cfg.elevated_threshold:.2f}"
        )
        level = ScreeningLevel.ELEVATED
    else:
        reasons.append(
            f"aggregate distress score {distress_score:.2f} below elevated threshold "
            f"{cfg.elevated_threshold:.2f}"
        )
        level = ScreeningLevel.NEUTRAL

    if top_class != "neutral" and level == ScreeningLevel.NEUTRAL:
        reasons.append(f"dominant non-neutral class is '{top_class}' ({top_prob:.2f})")

    return ScreeningResult(
        level=level,
        distress_score=distress_score,
        screening_probs=screening,
        raw_probs=raw,
        top_screening_class=top_class,
        top_screening_prob=top_prob,
        reasons=reasons,
    )
