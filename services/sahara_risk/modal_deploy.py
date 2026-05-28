"""Modal scheduled function — weekly per-user risk-score update.

Schedule: every Monday 00:00 UTC. For each user with an active 26-week
monitoring period, builds a WeeklyActivitySnapshot from Firestore, runs
[model.update_weekly], and persists the new profile + audit row.

The cron also handles two lifecycle transitions:

  * **DAST completed last week** → call [register_user_for_monitoring]
    (also invocable directly from the Android client after the user
    finishes the assessment) to build the MonitoringPeriod, drop the
    initial-notice popup doc, and seed the profile.
  * **26-week window finished** → generate the cumulative report, mark
    reassessment required, and clear per-cycle monitoring inputs.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

import modal

from .config import ACTIVATION_DELAY_WEEKS, MONITORING_WINDOW_WEEKS
from .features import snapshot_to_features


APP_NAME = "sahara-risk"
SCHEDULE_CRON = "0 0 * * 1"   # Mondays 00:00 UTC
TIMEOUT_SECONDS = 900

logger = logging.getLogger("sahara_risk.modal")


image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install("google-cloud-firestore==2.18.0")
    .add_local_python_source("sahara_risk")
)

app = modal.App(name=APP_NAME, image=image)


@app.function(
    image=image,
    timeout=TIMEOUT_SECONDS,
    schedule=modal.Cron(SCHEDULE_CRON),
    secrets=[
        modal.Secret.from_name(
            "sahara-firestore-secret",
            required_keys=["GOOGLE_APPLICATION_CREDENTIALS_JSON"],
        )
    ],
)
def run_weekly_risk_updates() -> dict:
    """Weekly per-user risk-score adjustment."""
    from sahara_risk.aggregator import build_snapshot
    from sahara_risk.firestore_writer import (
        complete_monitoring_cycle,
        list_monitored_users,
        load_profile,
        load_six_month_sleep_average,
        write_profile_and_update,
    )
    from sahara_risk.model import update_weekly
    from sahara_risk.monitoring import MonitoringPeriod

    now = datetime.now(timezone.utc)
    summary = {"processed": 0, "skipped": 0, "completed": 0, "errors": 0}

    for user in list_monitored_users():
        uid = user["uid"]
        try:
            period = MonitoringPeriod(
                user_id=uid,
                dast_completed_at=datetime.fromisoformat(user["dast_completed_at"]),
                monitoring_starts_at=datetime.fromisoformat(user["monitoring_starts_at"]),
                monitoring_ends_at=datetime.fromisoformat(user["monitoring_ends_at"]),
                duration_weeks=int(user.get("duration_weeks", MONITORING_WINDOW_WEEKS)),
            )
            if now >= period.monitoring_ends_at:
                complete_monitoring_cycle(uid, period, now=now)
                summary["completed"] += 1
                continue
            if now < period.monitoring_starts_at:
                summary["skipped"] += 1
                continue

            profile = load_profile(uid)
            if profile is None:
                logger.warning(f"user {uid} has monitoring doc but no profile; skipping")
                summary["skipped"] += 1
                continue

            week_start = now - timedelta(days=7)
            week_end = now
            avg_sleep = load_six_month_sleep_average(uid)
            snapshot = build_snapshot(uid, week_start, week_end, six_month_sleep_average=avg_sleep)
            vector = snapshot_to_features(snapshot)

            profile, update = update_weekly(
                profile=profile,
                feature_vector=vector,
                week_iso=week_start.isoformat(timespec="seconds"),
                now=now,
            )
            # Preserve the duration separately from the risk feature. Future
            # missing nights use the prior 26 weekly averages as their fallback.
            if snapshot.sleep is not None:
                update.feature_vector["__sleep_hours_avg"] = round(
                    snapshot.sleep.average_hours, 4
                )
            write_profile_and_update(profile, update)
            summary["processed"] += 1
        except Exception:
            logger.exception(f"weekly risk update failed for uid={uid}")
            summary["errors"] += 1
    return summary


@app.function(
    image=image,
    timeout=120,
    secrets=[
        modal.Secret.from_name(
            "sahara-firestore-secret",
            required_keys=["GOOGLE_APPLICATION_CREDENTIALS_JSON"],
        )
    ],
)
def register_user_for_monitoring(uid: str, dast_score: int, dast_completed_at_iso: str) -> dict:
    """Called from the Android app after the user completes their first DAST.

    Idempotent — re-calling overwrites the period + reseeds the profile.
    """
    from sahara_risk.firestore_writer import (
        write_initial_monitoring_notice,
        write_monitoring_period,
        write_profile_and_update,
    )
    from sahara_risk.model import WeeklyRiskUpdate, initialise_profile_from_dast
    from sahara_risk.monitoring import build_monitoring_period

    dast_completed_at = datetime.fromisoformat(dast_completed_at_iso)
    period = build_monitoring_period(uid, dast_completed_at)
    profile = initialise_profile_from_dast(uid, dast_score, period.monitoring_starts_at)

    write_monitoring_period(period)
    write_initial_monitoring_notice(uid, period)
    # Drop a synthetic "week 0" history row so the Android client has
    # something to graph from day 1.
    bootstrap = WeeklyRiskUpdate(
        user_id=uid,
        week_index=0,
        week_iso=period.dast_completed_at.isoformat(timespec="seconds"),
        previous_risk_score=profile.initial_risk_score,
        observation=profile.initial_risk_score,
        recovery_credit=0.0,
        raw_delta=0.0,
        applied_delta=0.0,
        new_risk_score=profile.initial_risk_score,
        alpha=1.0,
        feature_contributions={},
        feature_anomalies={},
        feature_vector={},
        reasons=["bootstrap from DAST score; monitoring window opens 1 week later"],
    )
    write_profile_and_update(profile, bootstrap)
    return {
        "ok": True,
        "monitoring_starts_at": period.monitoring_starts_at.isoformat(timespec="minutes"),
        "monitoring_ends_at":   period.monitoring_ends_at.isoformat(timespec="minutes"),
    }


@app.local_entrypoint()
def main() -> None:
    print(f"sahara_risk deployed. Cron: '{SCHEDULE_CRON}' (Mondays 00:00 UTC).")
    print("Register a user with:")
    print("  modal run sahara_risk/modal_deploy.py::register_user_for_monitoring --uid <uid> --dast-score <0..10> --dast-completed-at-iso <iso>")
