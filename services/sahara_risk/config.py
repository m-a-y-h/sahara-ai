"""Tunable constants for the per-user risk model.

Two principles guide the numbers below:

1. **Risk only moves slowly during calibration.** Weeks 1–4 use a high EWMA
   alpha (0.90) so a single bad week doesn't flip a low-DAST user into
   "high risk". Weeks 5–12 drop to 0.75, weeks 13+ to 0.60.

2. **Recovery points can counter the score.** The gamified tracker awards
   recovery credit per week; that credit is subtracted from the
   observation *before* the EWMA, so a strong recovery week can hold the
   score back even when a few flagged signals fire.

Everything here is a frozen dataclass so the model can deserialise a
snapshot's weights from a user's profile document later (e.g. if we ever
A/B different weight schedules per cohort).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping


# ---------------------------------------------------------------------------
# Monitoring window
# ---------------------------------------------------------------------------

# Activates one week after the DAST assessment and runs for 26 weeks.
MONITORING_WINDOW_WEEKS: int = 26
ACTIVATION_DELAY_WEEKS: int = 1


# ---------------------------------------------------------------------------
# DAST → initial risk score mapping
# ---------------------------------------------------------------------------
#
# DAST-10 score interpretation (from the validated instrument):
#   0       no problems reported
#   1-2     low level
#   3-5     moderate
#   6-8     substantial
#   9-10    severe
#
# We map the discrete score to a risk *floor* in [0, 1]. This is the value
# the EWMA starts from and the soft floor the score can decay toward.
DEFAULT_RISK_FROM_DAST: dict[int, float] = {
    0: 0.05,
    1: 0.15,
    2: 0.22,
    3: 0.35,
    4: 0.42,
    5: 0.50,
    6: 0.62,
    7: 0.70,
    8: 0.78,
    9: 0.88,
    10: 0.95,
}


def initial_risk_from_dast(score: int) -> float:
    """Clamp + lookup. Out-of-range scores get the nearest defined value."""
    s = max(0, min(score, max(DEFAULT_RISK_FROM_DAST)))
    return DEFAULT_RISK_FROM_DAST[s]


# ---------------------------------------------------------------------------
# EWMA alpha schedule
# ---------------------------------------------------------------------------
#
# Higher alpha = slower update (more memory). We want slow movement at the
# start because the per-user baselines aren't built up yet, then faster
# updates once we've seen ~3 months of weekly data.
EWMA_ALPHA_SCHEDULE: tuple[tuple[int, float], ...] = (
    # (max_week_index_inclusive, alpha)
    (4,  0.90),
    (12, 0.75),
    (26, 0.60),
)


def ewma_alpha_for_week(week_index: int) -> float:
    """Pick the alpha for the n-th update (1-based)."""
    for upper, alpha in EWMA_ALPHA_SCHEDULE:
        if week_index <= upper:
            return alpha
    # After week 26 we just keep the final alpha — should never fire since
    # monitoring stops, but doesn't hurt.
    return EWMA_ALPHA_SCHEDULE[-1][1]


# ---------------------------------------------------------------------------
# Per-source feature weights
# ---------------------------------------------------------------------------
#
# Each source contributes to the weekly observation via:
#     contribution = weight * normalised_signal
# Where normalised_signal is the per-user z-score of this week's value
# (clipped to [-2, 2] then mapped to [0, 1] via 0.5 + z/4).
#
# Weights sum to 1.0 across the "risk-increasing" sources so the unweighted
# observation stays in [0, 1]. Recovery is a counter-signal subtracted
# from the result, allowed to push the observation negative (clipped to 0).


@dataclass(frozen=True)
class FeatureWeights:
    """Per-source contribution weights for the weekly observation."""

    face_emotion:    float = 0.18   # 7 entries max per week
    voice_emotion:   float = 0.10   # 7 entries max per week
    chat_sentiment:  float = 0.14   # unlimited entries per week
    journal_mood:    float = 0.10   # 21 entries max per week
    sleep_quality:   float = 0.10   # 7 entries required per week (with fallback)
    listening_music: float = 0.10   # sahara_listening weekly aggregate
    social_posts:    float = 0.08   # bluesky + in-app community sentiment
    steam_games:     float = 0.05
    youtube_subs:    float = 0.05
    screen_time:     float = 0.10   # weighted by app reputation hash

    # Counter-signal — recovery points subtract from observation.
    recovery_credit: float = 0.20

    @property
    def risk_sources(self) -> Mapping[str, float]:
        return {
            "face_emotion":    self.face_emotion,
            "voice_emotion":   self.voice_emotion,
            "chat_sentiment":  self.chat_sentiment,
            "journal_mood":    self.journal_mood,
            "sleep_quality":   self.sleep_quality,
            "listening_music": self.listening_music,
            "social_posts":    self.social_posts,
            "steam_games":     self.steam_games,
            "youtube_subs":    self.youtube_subs,
            "screen_time":     self.screen_time,
        }


DEFAULT_FEATURE_WEIGHTS = FeatureWeights()


# ---------------------------------------------------------------------------
# Hard floors / ceilings the user's score cannot blow past in one week
# ---------------------------------------------------------------------------
#
# Even with a heavy recovery week the score can only drop this much per
# week. Symmetrically, a maximally bad week can't push it up more than
# this much in one go. Prevents whiplash for users who skip a week of
# activity entirely.
MAX_WEEKLY_DROP: float = 0.10
MAX_WEEKLY_RISE: float = 0.15
