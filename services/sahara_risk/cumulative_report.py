"""End-of-cycle 6-month cumulative report + the lifecycle reset rules.

When the 26-week monitoring window completes, the Modal cron does **three**
things in one transaction:

  1. Generates a [CumulativeRiskReport] from the user's stored ``risk_history``
     rows + the final ``risk_profile/current`` doc.
  2. Writes the report under ``users/{uid}/cumulative_reports/{cycle_id}``
     so the Android client can render the 6-month digest on a dedicated
     screen and the dashboard popup.
  3. Resets the per-user behaviour-cycle state. **Everything** under the
     user's per-week activity subcollections is wiped — the per-source
     detail is already captured in the report — **except** the chat history
     and the progress / gamified-recovery counter (those survive cycles,
     per the spec).

The report generator is pure-Python so we can unit-test the reset rules
without touching Firestore.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Iterable, Mapping, Optional

from .config import MONITORING_WINDOW_WEEKS
from .model import UserRiskProfile, WeeklyRiskUpdate


# ---------------------------------------------------------------------------
# Reset rules — explicit allow-list and deny-list
# ---------------------------------------------------------------------------

# Per-user subcollections that get wiped at cycle completion. Anything not
# on this list survives. Keeping this as a frozen tuple makes the contract
# easy to grep / review and prevents accidental scope creep.
RESET_SUBCOLLECTIONS: tuple[str, ...] = (
    "risk_profile",           # current EWMA state — superseded by the report
    "risk_history",           # 26 weekly audit rows — summarised in the report
    "sahara_lens_checkins",
    "sahara_voice_checkins",
    "activity_log_flags",
    "weekly_reports",
    "weekly_report_dismissals",
    "social_post_flags",
    "steam_play_flags",
    "youtube_sub_flags",
    "journal_entries",
    "sleep_logs",
    "screen_time_log",
    "monitoring",              # active monitoring window + start_notice; deleted last
)

# Per-user subcollections that **must** survive. Kept explicit so a reviewer
# can confirm the spec is preserved without grepping the negative case.
PRESERVED_SUBCOLLECTIONS: tuple[str, ...] = (
    "recovery_points_log",     # the gamified-recovery / progress counter
    "chat_messages",           # AI + counselor chat history
    "counselor_alerts",        # crisis escalation audit trail
    "cumulative_reports",      # past cycle digests
)


# ---------------------------------------------------------------------------
# Cumulative report dataclass
# ---------------------------------------------------------------------------


@dataclass
class CumulativeRiskReport:
    """One row at ``users/{uid}/cumulative_reports/{cycle_id}``.

    `cycle_id` is the monitoring window's start ISO timestamp so a user who
    completes multiple cycles (after taking the DAST again later on) gets
    one report per cycle with stable ordering.
    """

    user_id: str
    cycle_id: str
    monitoring_starts_at_iso: str
    monitoring_ends_at_iso: str
    initial_dast_score: int
    initial_risk_score: float
    final_risk_score: float
    overall_delta: float

    # 26-element list, oldest first, of `new_risk_score` after each weekly
    # update. Length may be shorter if the user joined mid-cycle.
    risk_trajectory: list[float] = field(default_factory=list)

    # weeks the user spent in each discrete bucket (low/moderate/substantial/severe).
    weeks_in_severity: dict[str, int] = field(default_factory=dict)

    # Per-source summary — for each of the 10 risk feature sources we
    # report mean contribution + max contribution + how many weeks the
    # source was present at all. Powers the "what drove your score" rings
    # on the Android screen.
    feature_source_summary: dict[str, dict[str, float]] = field(default_factory=dict)

    total_recovery_credit: float = 0.0
    completed_weeks: int = 0
    weeks_with_no_evidence: int = 0
    generated_at_iso: Optional[str] = None
    model_version: str = "rules-1.0"
    acknowledged: bool = False

    def to_firestore_dict(self) -> dict:
        return {
            "user_id":               self.user_id,
            "cycle_id":              self.cycle_id,
            "monitoring_starts_at":  self.monitoring_starts_at_iso,
            "monitoring_ends_at":    self.monitoring_ends_at_iso,
            "initial_dast_score":    self.initial_dast_score,
            "initial_risk_score":    round(self.initial_risk_score, 4),
            "final_risk_score":      round(self.final_risk_score, 4),
            "overall_delta":         round(self.overall_delta, 4),
            "risk_trajectory":       [round(v, 4) for v in self.risk_trajectory],
            "weeks_in_severity":     dict(self.weeks_in_severity),
            "feature_source_summary": {
                k: {kk: round(vv, 4) for kk, vv in v.items()}
                for k, v in self.feature_source_summary.items()
            },
            "total_recovery_credit": round(self.total_recovery_credit, 4),
            "completed_weeks":       self.completed_weeks,
            "weeks_with_no_evidence": self.weeks_with_no_evidence,
            "generated_at":          self.generated_at_iso,
            "model_version":         self.model_version,
            "acknowledged":          self.acknowledged,
        }


# ---------------------------------------------------------------------------
# Severity bucketing (matches the Android dashboard ring)
# ---------------------------------------------------------------------------


def _severity_for_score(score: float) -> str:
    if score < 0.20:
        return "low"
    if score < 0.45:
        return "moderate"
    if score < 0.70:
        return "substantial"
    return "severe"


# ---------------------------------------------------------------------------
# Generator
# ---------------------------------------------------------------------------


def generate_cumulative_report(
    profile: UserRiskProfile,
    history: Iterable[Mapping[str, object]],
    *,
    monitoring_ends_at: Optional[datetime] = None,
    now: Optional[datetime] = None,
) -> CumulativeRiskReport:
    """Build a [CumulativeRiskReport] from the profile + every history row.

    ``history`` is the raw list of dicts as returned by Firestore (or by
    [WeeklyRiskUpdate.to_firestore_dict()]) — we tolerate either shape so
    the unit tests can pass synthetic data without Firestore in scope.

    The bootstrap row written at register_user_for_monitoring (week_index
    == 0) is dropped from the trajectory because it just records the
    initial DAST mapping, not a real weekly update.
    """
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    if monitoring_ends_at is None:
        monitoring_ends_at = profile.monitoring_starts_at + _window_delta()
    if monitoring_ends_at.tzinfo is None:
        monitoring_ends_at = monitoring_ends_at.replace(tzinfo=timezone.utc)

    rows: list[Mapping[str, object]] = []
    for row in history:
        if int(row.get("week_index") or 0) <= 0:
            continue
        rows.append(row)
    rows.sort(key=lambda r: int(r.get("week_index") or 0))

    trajectory: list[float] = []
    weeks_in_severity: dict[str, int] = {
        "low": 0, "moderate": 0, "substantial": 0, "severe": 0
    }
    feature_sums: dict[str, dict[str, float]] = {}
    total_recovery = 0.0
    weeks_with_no_evidence = 0

    for row in rows:
        score = float(row.get("new_risk_score") or 0.0)
        trajectory.append(score)
        weeks_in_severity[_severity_for_score(score)] += 1
        total_recovery += float(row.get("recovery_credit") or 0.0)
        contributions = row.get("feature_contributions") or {}
        if not contributions:
            weeks_with_no_evidence += 1
        for source, value in contributions.items():
            slot = feature_sums.setdefault(source, {"sum": 0.0, "max": 0.0, "weeks_present": 0.0})
            slot["sum"] += float(value)
            slot["max"] = max(slot["max"], float(value))
            slot["weeks_present"] += 1

    feature_source_summary: dict[str, dict[str, float]] = {}
    for source, slot in feature_sums.items():
        n = slot["weeks_present"] or 1.0
        feature_source_summary[source] = {
            "mean_contribution": slot["sum"] / n,
            "max_contribution":  slot["max"],
            "weeks_present":     slot["weeks_present"],
        }

    return CumulativeRiskReport(
        user_id=profile.user_id,
        cycle_id=profile.monitoring_starts_at.isoformat(timespec="minutes"),
        monitoring_starts_at_iso=profile.monitoring_starts_at.isoformat(timespec="minutes"),
        monitoring_ends_at_iso=monitoring_ends_at.isoformat(timespec="minutes"),
        initial_dast_score=profile.initial_dast_score,
        initial_risk_score=profile.initial_risk_score,
        final_risk_score=profile.current_risk_score,
        overall_delta=profile.current_risk_score - profile.initial_risk_score,
        risk_trajectory=trajectory,
        weeks_in_severity=weeks_in_severity,
        feature_source_summary=feature_source_summary,
        total_recovery_credit=total_recovery,
        completed_weeks=len(trajectory),
        weeks_with_no_evidence=weeks_with_no_evidence,
        generated_at_iso=now.isoformat(timespec="minutes"),
    )


def _window_delta():
    from datetime import timedelta
    return timedelta(weeks=MONITORING_WINDOW_WEEKS)


# ---------------------------------------------------------------------------
# Reset-rule helpers — pure-Python so they're testable without Firestore
# ---------------------------------------------------------------------------


def should_reset(subcollection: str) -> bool:
    """Authority on whether a per-user subcollection gets wiped on cycle end."""
    return subcollection in RESET_SUBCOLLECTIONS


def must_preserve(subcollection: str) -> bool:
    """Authority on whether a per-user subcollection MUST survive the reset."""
    return subcollection in PRESERVED_SUBCOLLECTIONS
