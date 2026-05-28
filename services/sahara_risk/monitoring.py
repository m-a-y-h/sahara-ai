"""6-month monitoring window — start/end logic.

The monitoring period is the operational definition the dashboard popup
quotes back at the user when it announces "your monitoring has started".
The same dataclass round-trips to Firestore for the Android client to
read.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional

from .config import ACTIVATION_DELAY_WEEKS, MONITORING_WINDOW_WEEKS


@dataclass(frozen=True)
class MonitoringPeriod:
    """Per-user start/end timestamps for the 26-week monitoring window."""

    user_id: str
    dast_completed_at: datetime
    monitoring_starts_at: datetime  # = dast_completed_at + ACTIVATION_DELAY_WEEKS
    monitoring_ends_at: datetime    # = starts + MONITORING_WINDOW_WEEKS
    duration_weeks: int = MONITORING_WINDOW_WEEKS

    def to_firestore_dict(self) -> dict:
        return {
            "user_id":              self.user_id,
            "dast_completed_at":    self.dast_completed_at.isoformat(timespec="seconds"),
            "monitoring_starts_at": self.monitoring_starts_at.isoformat(timespec="seconds"),
            "monitoring_ends_at":   self.monitoring_ends_at.isoformat(timespec="seconds"),
            "duration_weeks":       self.duration_weeks,
        }


def build_monitoring_period(
    user_id: str,
    dast_completed_at: datetime,
    activation_delay_weeks: int = ACTIVATION_DELAY_WEEKS,
    window_weeks: int = MONITORING_WINDOW_WEEKS,
) -> MonitoringPeriod:
    """Construct the window from a DAST completion timestamp."""
    if dast_completed_at.tzinfo is None:
        dast_completed_at = dast_completed_at.replace(tzinfo=timezone.utc)
    starts = dast_completed_at + timedelta(weeks=activation_delay_weeks)
    ends = starts + timedelta(weeks=window_weeks)
    return MonitoringPeriod(
        user_id=user_id,
        dast_completed_at=dast_completed_at,
        monitoring_starts_at=starts,
        monitoring_ends_at=ends,
        duration_weeks=window_weeks,
    )


def is_inside_monitoring(period: MonitoringPeriod, now: Optional[datetime] = None) -> bool:
    """True if the current time falls inside the active monitoring window."""
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    return period.monitoring_starts_at <= now < period.monitoring_ends_at


def weeks_remaining(period: MonitoringPeriod, now: Optional[datetime] = None) -> int:
    """How many full weeks are left in the monitoring window. Never < 0."""
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    if now >= period.monitoring_ends_at:
        return 0
    if now < period.monitoring_starts_at:
        return period.duration_weeks
    delta = period.monitoring_ends_at - now
    return max(0, delta.days // 7)


def weeks_elapsed(period: MonitoringPeriod, now: Optional[datetime] = None) -> int:
    """Index of the *current* weekly update (1-based). 0 if before the window."""
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    if now < period.monitoring_starts_at:
        return 0
    if now >= period.monitoring_ends_at:
        return period.duration_weeks
    delta = now - period.monitoring_starts_at
    return min(period.duration_weeks, max(1, delta.days // 7 + 1))
