"""Persistence layer for the risk profile + weekly history."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Iterator, Optional

from .cumulative_report import CumulativeRiskReport, RESET_SUBCOLLECTIONS, generate_cumulative_report
from .model import FeatureRunningStats, UserRiskProfile, WeeklyRiskUpdate
from .monitoring import MonitoringPeriod

logger = logging.getLogger("sahara_risk.firestore")

PROFILE_DOC_ID = "current"


def _client():
    from google.cloud import firestore  # type: ignore
    return firestore.Client()


# ---------------------------------------------------------------------------
# Reads
# ---------------------------------------------------------------------------


def list_monitored_users() -> Iterator[dict]:
    """Iterate over every user with an active monitoring period."""
    db = _client()
    for snap in db.collection_group("monitoring").stream():
        data = snap.to_dict() or {}
        if not data.get("monitoring_starts_at") or not data.get("monitoring_ends_at"):
            continue
        uid = snap.reference.parent.parent.id if snap.reference.parent.parent else None
        if not uid:
            continue
        yield {"uid": uid, **data}


def load_profile(uid: str) -> Optional[UserRiskProfile]:
    """Hydrate a UserRiskProfile from Firestore. Returns None if missing."""
    from datetime import datetime
    db = _client()
    snap = (
        db.collection("users").document(uid)
        .collection("risk_profile").document(PROFILE_DOC_ID).get()
    )
    if not snap.exists:
        return None
    d = snap.to_dict() or {}
    return UserRiskProfile(
        user_id=uid,
        initial_dast_score=int(d.get("initial_dast_score") or 0),
        initial_risk_score=float(d.get("initial_risk_score") or 0.0),
        current_risk_score=float(d.get("current_risk_score") or 0.0),
        monitoring_starts_at=datetime.fromisoformat(d["monitoring_starts_at"]),
        week_index=int(d.get("week_index") or 0),
        running_stats={
            k: FeatureRunningStats.from_dict(v)
            for k, v in (d.get("running_stats") or {}).items()
        },
        last_updated_at=(
            datetime.fromisoformat(d["last_updated_at"]) if d.get("last_updated_at") else None
        ),
    )


def load_six_month_sleep_average(uid: str) -> Optional[float]:
    """Average of the last 26 weekly snapshots' average_hours, if present.

    Used as the sleep fallback when imported and self-reported records are both
    missing for the week. Falls back to None (i.e. the model uses
    DEFAULT_SLEEP_HOURS) for first-week users.
    """
    db = _client()
    coll = (
        db.collection("users").document(uid)
        .collection("risk_history")
        .order_by("week_iso", direction=__firestore_descending__())
        .limit(26)
    )
    hours: list[float] = []
    for snap in coll.stream():
        d = snap.to_dict() or {}
        fv = d.get("feature_vector") or {}
        # The weekly cron persists duration separately from the risk signal.
        v = fv.get("__sleep_hours_avg")
        if isinstance(v, (int, float)) and v > 0:
            hours.append(float(v))
    return (sum(hours) / len(hours)) if hours else None


def load_risk_history(uid: str) -> list[dict]:
    """Return every weekly audit row for the active cycle."""
    db = _client()
    coll = db.collection("users").document(uid).collection("risk_history")
    rows: list[dict] = []
    for snap in coll.stream():
        rows.append(snap.to_dict() or {})
    return rows


def __firestore_descending__():
    from google.cloud import firestore  # type: ignore
    return firestore.Query.DESCENDING


# ---------------------------------------------------------------------------
# Writes
# ---------------------------------------------------------------------------


def write_monitoring_period(period: MonitoringPeriod) -> None:
    db = _client()
    user_ref = db.collection("users").document(period.user_id)
    batch = db.batch()
    batch.set(
        user_ref.collection("monitoring").document("active"),
        period.to_firestore_dict(),
        merge=False,
    )
    batch.set(
        user_ref.collection("lifecycle").document("current"),
        {
            "assessment_required": False,
            "active_cycle_id": period.monitoring_starts_at.isoformat(timespec="minutes"),
            "monitoring_starts_at": period.monitoring_starts_at.isoformat(timespec="minutes"),
            "monitoring_ends_at": period.monitoring_ends_at.isoformat(timespec="minutes"),
            "updated_at": datetime.now(timezone.utc).isoformat(timespec="minutes"),
        },
        merge=True,
    )
    batch.commit()


def clear_monitoring_period(uid: str) -> None:
    """Called when the 26-week window completes naturally."""
    db = _client()
    (
        db.collection("users").document(uid)
        .collection("monitoring").document("active")
        .delete()
    )


def complete_monitoring_cycle(
    uid: str,
    period: MonitoringPeriod,
    *,
    now: Optional[datetime] = None,
) -> Optional[CumulativeRiskReport]:
    """Generate the 6-month report, mark reassessment required, then reset.

    This is intentionally idempotent. If the weekly cron retries a completed
    user before the ``monitoring`` subcollection is deleted, the same report
    document is overwritten and the reset is attempted again.
    """
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)

    profile = load_profile(uid)
    if profile is None:
        logger.warning("user %s reached cycle end but has no risk profile", uid)
        clear_monitoring_period(uid)
        write_assessment_cycle_status(
            uid=uid,
            assessment_required=True,
            completed_at=now,
            latest_report_id=None,
        )
        return None

    history = load_risk_history(uid)
    report = generate_cumulative_report(
        profile=profile,
        history=history,
        monitoring_ends_at=period.monitoring_ends_at,
        now=now,
    )
    write_cumulative_report_and_cycle_status(
        report=report,
        completed_at=now,
    )
    reset_cycle_subcollections(uid)
    return report


def write_profile_and_update(
    profile: UserRiskProfile,
    update: WeeklyRiskUpdate,
) -> None:
    """Atomic-ish persist of the new profile state + the audit row."""
    db = _client()
    batch = db.batch()

    profile_ref = (
        db.collection("users").document(profile.user_id)
        .collection("risk_profile").document(PROFILE_DOC_ID)
    )
    history_ref = (
        db.collection("users").document(profile.user_id)
        .collection("risk_history").document(update.week_iso)
    )
    batch.set(profile_ref, profile.to_firestore_dict(), merge=False)
    batch.set(history_ref, update.to_firestore_dict(), merge=False)
    batch.commit()


def write_cumulative_report_and_cycle_status(
    report: CumulativeRiskReport,
    *,
    completed_at: datetime,
) -> None:
    db = _client()
    batch = db.batch()
    user_ref = db.collection("users").document(report.user_id)
    report_ref = user_ref.collection("cumulative_reports").document(report.cycle_id)
    status_ref = user_ref.collection("lifecycle").document("current")
    completed_iso = completed_at.isoformat(timespec="minutes")
    completed_epoch_ms = int(completed_at.timestamp() * 1000)

    batch.set(report_ref, report.to_firestore_dict(), merge=False)
    batch.set(
        status_ref,
        {
            "assessment_required": True,
            "completed_at": completed_iso,
            "completed_at_epoch_ms": completed_epoch_ms,
            "latest_report_id": report.cycle_id,
            "current_cycle_id": report.cycle_id,
            "report_acknowledged": False,
            "updated_at": completed_iso,
        },
        merge=True,
    )
    batch.commit()


def write_assessment_cycle_status(
    uid: str,
    *,
    assessment_required: bool,
    completed_at: datetime,
    latest_report_id: Optional[str],
) -> None:
    db = _client()
    completed_iso = completed_at.isoformat(timespec="minutes")
    db.collection("users").document(uid).collection("lifecycle").document("current").set(
        {
            "assessment_required": assessment_required,
            "completed_at": completed_iso,
            "completed_at_epoch_ms": int(completed_at.timestamp() * 1000),
            "latest_report_id": latest_report_id,
            "current_cycle_id": latest_report_id,
            "report_acknowledged": latest_report_id is None,
            "updated_at": completed_iso,
        },
        merge=True,
    )


def reset_cycle_subcollections(uid: str) -> dict[str, int]:
    """Delete per-cycle user subcollections after the report has been saved."""
    db = _client()
    user_ref = db.collection("users").document(uid)
    deleted: dict[str, int] = {}
    ordered = [c for c in RESET_SUBCOLLECTIONS if c != "monitoring"] + ["monitoring"]
    for subcollection in ordered:
        deleted[subcollection] = _delete_collection(db, user_ref.collection(subcollection))
    return deleted


def _delete_collection(db, collection_ref, batch_size: int = 300) -> int:
    total = 0
    while True:
        docs = list(collection_ref.limit(batch_size).stream())
        if not docs:
            return total
        batch = db.batch()
        for doc in docs:
            batch.delete(doc.reference)
        batch.commit()
        total += len(docs)


def write_initial_monitoring_notice(uid: str, period: MonitoringPeriod) -> None:
    """Pop-up doc the Android dashboard reads to show the "monitoring started"
    modal exactly once."""
    db = _client()
    (
        db.collection("users").document(uid)
        .collection("monitoring").document("start_notice")
        .set(
            {
                "shown": False,
                "monitoring_starts_at": period.monitoring_starts_at.isoformat(timespec="minutes"),
                "monitoring_ends_at":   period.monitoring_ends_at.isoformat(timespec="minutes"),
                "duration_weeks":       period.duration_weeks,
                "notice_text_en": (
                    "Sahara's 6-month behaviour-cycle monitoring has begun. "
                    "Use the app honestly — spoofing or trolling will produce "
                    "an inaccurate score that helps no one. You can review your "
                    "current risk score on the dashboard at any time."
                ),
                "notice_text_ur": (
                    "Sahara ka 6-month behaviour-cycle monitoring shuru ho gaya hai. "
                    "App ka istemal imandari se karein — spoof ya troll karne par "
                    "score galat ho jayega aur kisi ko faida nahi hoga. Aap apna "
                    "current risk score dashboard par kabhi bhi dekh sakte hain."
                ),
            },
            merge=False,
        )
    )
