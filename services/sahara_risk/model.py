"""The per-user unsupervised risk model.

Each user owns one [UserRiskProfile]. Each weekly cron run calls
[update_weekly], which:

  1. Builds per-source signals from the week's [WeeklyFeatureVector].
  2. Updates the user's running mean+variance for each source (so they
     become their own baseline — true unsupervised behaviour).
  3. Computes a per-feature **anomaly value** by z-scoring this week's
     signal against the running baseline, clipping to ±2σ, and remapping
     into `[0, 1]`.
  4. Aggregates the anomalies by weight, subtracts recovery credit, and
     EWMAs the result into the existing risk score with the alpha for
     the current week index.
  5. Clamps the per-week delta to [MAX_WEEKLY_DROP, MAX_WEEKLY_RISE] so
     the score can't whiplash in either direction.

The math is intentionally simple — a Bayesian online estimator or an
isolation forest would be overkill given 26 weekly data points per user.
The EWMA + per-user z-score combination has the desirable properties
(per-user adaptation, audit-friendly, no training set required).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional

from .config import (
    DEFAULT_FEATURE_WEIGHTS,
    FeatureWeights,
    MAX_WEEKLY_DROP,
    MAX_WEEKLY_RISE,
    MONITORING_WINDOW_WEEKS,
    ewma_alpha_for_week,
    initial_risk_from_dast,
)
from .features import WeeklyFeatureVector


# ---------------------------------------------------------------------------
# Running statistics — Welford's algorithm
# ---------------------------------------------------------------------------


@dataclass
class FeatureRunningStats:
    """Per-feature running mean + variance using Welford's online algorithm."""

    count: int = 0
    mean: float = 0.0
    m2: float = 0.0  # sum of squared deviations from the running mean

    def update(self, value: float) -> None:
        self.count += 1
        delta = value - self.mean
        self.mean += delta / self.count
        self.m2 += delta * (value - self.mean)

    @property
    def variance(self) -> float:
        return self.m2 / self.count if self.count > 1 else 0.0

    @property
    def std(self) -> float:
        return self.variance ** 0.5

    def to_dict(self) -> dict[str, float | int]:
        return {"count": self.count, "mean": self.mean, "m2": self.m2}

    @classmethod
    def from_dict(cls, d: dict[str, float | int]) -> "FeatureRunningStats":
        return cls(
            count=int(d.get("count", 0)),
            mean=float(d.get("mean", 0.0)),
            m2=float(d.get("m2", 0.0)),
        )


# ---------------------------------------------------------------------------
# Profile
# ---------------------------------------------------------------------------


@dataclass
class UserRiskProfile:
    """All persistent state the weekly update needs.

    Stored at Firestore ``users/{uid}/risk_profile/current``. The model is
    deliberately small (single document) so the weekly cron round-trips
    cheaply and a screen on the Android client can show the current score
    + 6-month trend without paging.
    """

    user_id: str
    initial_dast_score: int
    initial_risk_score: float
    current_risk_score: float
    monitoring_starts_at: datetime
    week_index: int = 0                            # 0 before first update, 1..26 after
    running_stats: dict[str, FeatureRunningStats] = field(default_factory=dict)
    last_updated_at: Optional[datetime] = None

    @property
    def is_complete(self) -> bool:
        return self.week_index >= MONITORING_WINDOW_WEEKS

    def to_firestore_dict(self) -> dict:
        return {
            "user_id":              self.user_id,
            "initial_dast_score":   self.initial_dast_score,
            "initial_risk_score":   round(self.initial_risk_score, 4),
            "current_risk_score":   round(self.current_risk_score, 4),
            "monitoring_starts_at": self.monitoring_starts_at.isoformat(timespec="seconds"),
            "week_index":           self.week_index,
            "running_stats":        {k: v.to_dict() for k, v in self.running_stats.items()},
            "last_updated_at":      self.last_updated_at.isoformat(timespec="seconds") if self.last_updated_at else None,
        }


@dataclass
class WeeklyRiskUpdate:
    """One row appended to ``users/{uid}/risk_history/{week_iso}`` per run."""

    user_id: str
    week_index: int
    week_iso: str
    previous_risk_score: float
    observation: float
    recovery_credit: float
    raw_delta: float            # observation - previous (before EWMA + clamp)
    applied_delta: float        # actual change to the score after EWMA + clamp
    new_risk_score: float
    alpha: float
    feature_contributions: dict[str, float]
    feature_anomalies: dict[str, float]
    feature_vector: dict[str, Optional[float]]
    reasons: list[str] = field(default_factory=list)

    def to_firestore_dict(self) -> dict:
        return {
            "user_id":              self.user_id,
            "week_index":           self.week_index,
            "week_iso":             self.week_iso,
            "previous_risk_score":  round(self.previous_risk_score, 4),
            "observation":          round(self.observation, 4),
            "recovery_credit":      round(self.recovery_credit, 4),
            "raw_delta":            round(self.raw_delta, 4),
            "applied_delta":        round(self.applied_delta, 4),
            "new_risk_score":       round(self.new_risk_score, 4),
            "alpha":                round(self.alpha, 4),
            "feature_contributions": {k: round(v, 4) for k, v in self.feature_contributions.items()},
            "feature_anomalies":    {k: round(v, 4) for k, v in self.feature_anomalies.items()},
            "feature_vector":       self.feature_vector,
            "reasons":              list(self.reasons),
        }


# ---------------------------------------------------------------------------
# Initialisation
# ---------------------------------------------------------------------------


def initialise_profile_from_dast(
    user_id: str,
    dast_score: int,
    monitoring_starts_at: datetime,
) -> UserRiskProfile:
    """Build a fresh profile from the user's first DAST result."""
    initial = initial_risk_from_dast(dast_score)
    if monitoring_starts_at.tzinfo is None:
        monitoring_starts_at = monitoring_starts_at.replace(tzinfo=timezone.utc)
    return UserRiskProfile(
        user_id=user_id,
        initial_dast_score=dast_score,
        initial_risk_score=initial,
        current_risk_score=initial,
        monitoring_starts_at=monitoring_starts_at,
    )


# ---------------------------------------------------------------------------
# Weekly update
# ---------------------------------------------------------------------------


def _zscore_to_unit(value: float, mean: float, std: float) -> float:
    """Map a per-feature observation to `[0, 1]` against the running baseline.

    With < 2 weeks of history (std == 0) we just pass the raw value through —
    the user is their own baseline only once we've seen at least two weeks
    of evidence for the feature.
    """
    if std <= 1e-6:
        return max(0.0, min(1.0, value))
    z = (value - mean) / std
    z_clipped = max(-2.0, min(2.0, z))
    # Map [-2, 2] → [0, 1] so a value 2σ above the user's mean reads as 1.0.
    return 0.5 + (z_clipped / 4.0)


def update_weekly(
    profile: UserRiskProfile,
    feature_vector: WeeklyFeatureVector,
    week_iso: str,
    weights: FeatureWeights = DEFAULT_FEATURE_WEIGHTS,
    now: Optional[datetime] = None,
) -> tuple[UserRiskProfile, WeeklyRiskUpdate]:
    """Apply one weekly update to ``profile`` and return the new profile + audit row.

    Pure function — does not write to Firestore. The Modal cron calls
    [firestore_writer.write_profile_and_update] with the returned objects.
    """
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)

    week_index = profile.week_index + 1
    alpha = ewma_alpha_for_week(week_index)
    reasons: list[str] = []

    risk_source_weights = weights.risk_sources
    weight_sum = sum(risk_source_weights.values())  # for re-normalisation when some sources missing

    feature_anomalies: dict[str, float] = {}
    feature_contributions: dict[str, float] = {}
    observation_numerator = 0.0
    observation_denominator = 0.0

    fv = feature_vector.to_dict()
    for source, weight in risk_source_weights.items():
        signal = fv.get(source)
        if signal is None:
            continue   # missing → contributes nothing, does not penalise
        stats = profile.running_stats.setdefault(source, FeatureRunningStats())
        # Update *before* z-scoring so the user's own week-1 value sits at
        # the centre of their distribution (z = 0) rather than at the
        # extreme. This is what makes the user their own baseline.
        stats.update(signal)
        anomaly = _zscore_to_unit(signal, stats.mean, stats.std)
        feature_anomalies[source] = anomaly
        contribution = weight * anomaly
        feature_contributions[source] = contribution
        observation_numerator += contribution
        observation_denominator += weight

    # If the user contributed *some* sources this week, normalise so the
    # observation stays in [0, 1] even when only a subset of sources fired.
    if observation_denominator > 0:
        observation = observation_numerator / observation_denominator
    else:
        # No sources at all — fall back to the previous score so the EWMA
        # is a no-op for this week.
        observation = profile.current_risk_score
        reasons.append("no weekly evidence; risk score held steady")

    # Recovery credit subtracts from the observation (can push it negative;
    # we floor to 0). Recovery is rewarded only when there *was* some
    # weekly activity at all — pure-zero weeks shouldn't earn credit too.
    recovery_credit = (fv.get("recovery_credit") or 0.0) * weights.recovery_credit
    if observation_denominator > 0:
        observation = max(0.0, observation - recovery_credit)
        if recovery_credit > 0:
            reasons.append(
                f"recovery credit subtracted {recovery_credit:.3f} from observation"
            )

    raw_delta = observation - profile.current_risk_score
    ewma_target = alpha * profile.current_risk_score + (1 - alpha) * observation
    delta = ewma_target - profile.current_risk_score

    # Clamp per-week movement.
    if delta > MAX_WEEKLY_RISE:
        delta = MAX_WEEKLY_RISE
        reasons.append(f"weekly rise capped at {MAX_WEEKLY_RISE:.2f}")
    elif delta < -MAX_WEEKLY_DROP:
        delta = -MAX_WEEKLY_DROP
        reasons.append(f"weekly drop capped at {MAX_WEEKLY_DROP:.2f}")

    new_score = max(0.0, min(1.0, profile.current_risk_score + delta))
    update = WeeklyRiskUpdate(
        user_id=profile.user_id,
        week_index=week_index,
        week_iso=week_iso,
        previous_risk_score=profile.current_risk_score,
        observation=observation,
        recovery_credit=recovery_credit,
        raw_delta=raw_delta,
        applied_delta=delta,
        new_risk_score=new_score,
        alpha=alpha,
        feature_contributions=feature_contributions,
        feature_anomalies=feature_anomalies,
        feature_vector=fv,
        reasons=reasons,
    )

    # Build the next profile state. dataclass.replace would be slightly
    # cleaner but we want to keep ``running_stats`` shared so the update
    # we did in-place above persists.
    profile.current_risk_score = new_score
    profile.week_index = week_index
    profile.last_updated_at = now
    return profile, update
