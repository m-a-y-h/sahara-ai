"""Modal scheduled function — weekly listening risk report.

> Read the [POLICY WARNING] in ``sahara_listening/__init__.py`` first.
> This deployment is gated behind the ``SAHARA_LISTENING_RESEARCH_MODE``
> environment variable so it cannot run by accident.

Deploy
------

::

    modal deploy sahara_listening/modal_deploy.py

The schedule (every Sunday at 18:00 UTC = Monday 00:00 PKT) is the
"end-of-week" digest the user sees on the Sahara dashboard.

How user enrolment works
------------------------

The cron iterates over the Firestore collection
``users/{uid}/integrations/spotify`` and for each doc with
``listening_analysis_opt_in == true`` and a non-empty ``refresh_token`` field
it runs the weekly fetch.

The refresh token must be stored server-side after the user goes through the
ConnectionsScreen Spotify OAuth flow. The current PoC keeps it client-side
only (in ``SpotifyOAuthCallbackStore``); promoting that to a Firestore field
is a separate piece of work and **must** be paired with the user disclosure
update described in the project wiki.
"""

from __future__ import annotations

import logging
import os
from datetime import datetime, timezone

import modal


APP_NAME = "sahara-listening"
SCHEDULE_CRON = "0 18 * * 0"   
TIMEOUT_SECONDS = 900

logger = logging.getLogger("sahara_listening.modal")


image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "requests==2.32.3",
        "google-cloud-firestore==2.18.0",
    )
    .add_local_python_source("sahara_listening")
)

app = modal.App(name=APP_NAME, image=image)


@app.function(
    image=image,
    timeout=TIMEOUT_SECONDS,
    secrets=[
        modal.Secret.from_name(
            "sahara-spotify-secret",
            required_keys=["SPOTIFY_CLIENT_ID"],
        ),
        modal.Secret.from_name(
            "sahara-firestore-secret",
            required_keys=["GOOGLE_APPLICATION_CREDENTIALS_JSON"],
        ),
    ],
    schedule=modal.Cron(SCHEDULE_CRON),
)
def run_weekly_listening_report() -> dict:
    """Hourly Spotify weekly digest entrypoint.

    Pulls all opted-in users from Firestore, refreshes their Spotify tokens,
    fetches the last ~50 played tracks plus audio features and artist
    genres, runs the classifier, and writes a single
    ``users/{uid}/weekly_reports/{week_start_iso}`` document plus per-track
    flagged-activity rows under
    ``users/{uid}/activity_log_flags/{auto-id}``.
    """
    if not _truthy(os.environ.get("SAHARA_LISTENING_RESEARCH_MODE")):
        logger.warning(
            "SAHARA_LISTENING_RESEARCH_MODE is not set — refusing to run. "
            "Set it explicitly in the Modal app config to enable the cron."
        )
        return {"ran": False, "reason": "research_mode_disabled"}

    
    from sahara_listening.spotify_client import (
        artist_genres,
        normalize_recently_played,
        recently_played,
        refresh_access_token,
        track_audio_features,
    )
    from sahara_listening.weekly_report import aggregate_weekly_report, current_week_window
    from sahara_listening.firestore_writer import (
        list_opted_in_users,
        write_weekly_report,
        write_flagged_tracks,
    )

    client_id = os.environ["SPOTIFY_CLIENT_ID"]
    client_secret = os.environ.get("SPOTIFY_CLIENT_SECRET")  
    week_start, week_end = current_week_window(datetime.now(timezone.utc))

    summary = {"processed": 0, "errors": 0, "skipped": 0, "week_start": week_start.isoformat()}
    for user in list_opted_in_users():
        uid = user["uid"]
        refresh_token = user.get("spotify_refresh_token")
        if not refresh_token:
            summary["skipped"] += 1
            continue
        try:
            access_token = refresh_access_token(refresh_token, client_id, client_secret)
            if not access_token:
                summary["errors"] += 1
                continue

            items = recently_played(access_token, limit=50)
            track_ids = [(i.get("track") or {}).get("id") for i in items]
            artist_ids = list({
                ((i.get("track") or {}).get("artists") or [{}])[0].get("id")
                for i in items
                if (i.get("track") or {}).get("artists")
            } - {None})

            af = track_audio_features(access_token, [t for t in track_ids if t])
            genres = artist_genres(access_token, artist_ids)

            tracks = normalize_recently_played(items, audio_features=af, genres_by_artist=genres)
            report = aggregate_weekly_report(uid, tracks, week_start=week_start, week_end=week_end)

            write_weekly_report(uid, report)
            write_flagged_tracks(uid, report.flagged_tracks)
            summary["processed"] += 1
        except Exception:
            logger.exception(f"weekly listening run failed for uid={uid}")
            summary["errors"] += 1
    return summary


def _truthy(value):
    if value is None:
        return False
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


@app.local_entrypoint()
def main() -> None:
    print("sahara_listening modal app deployed.")
    print(f"Cron: '{SCHEDULE_CRON}' (Sundays 18:00 UTC).")
    print("Trigger manually with: modal run sahara_listening/modal_deploy.py::run_weekly_listening_report")
