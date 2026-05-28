"""Firestore writer for the weekly listening cron.

Wraps the four Firestore reads/writes the Modal cron needs:

  1. ``list_opted_in_users()``  →  iterator over users with consent
  2. ``write_weekly_report(...)``
  3. ``write_flagged_tracks(...)``
  4. ``delete_user_history(...)``  — exposed so the Android client's
     "delete my listening data" path has a server-side equivalent.

These are kept here (rather than in ``modal_deploy.py``) so we can also
import them from a local CLI for manual catch-up runs without round-tripping
through Modal.
"""

from __future__ import annotations

import logging
from typing import Iterable, Iterator

from .classifier import FlaggedTrack
from .weekly_report import WeeklyListeningReport
from .config import LISTENING_DATA_NAMESPACE

logger = logging.getLogger("sahara_listening.firestore")


def _client():
    from google.cloud import firestore  
    return firestore.Client()


def list_opted_in_users() -> Iterator[dict]:
    """Yield ``{"uid": ..., "spotify_refresh_token": ...}`` rows.

    Filter: doc must have ``listening_analysis_opt_in == True`` and a
    non-empty refresh token. Stored under
    ``users/{uid}/integrations/spotify`` (one doc per provider).
    """
    db = _client()
    coll = db.collection_group("integrations")
    for snap in coll.where("provider", "==", "spotify").stream():
        data = snap.to_dict() or {}
        if not data.get("listening_analysis_opt_in"):
            continue
        if not data.get("spotify_refresh_token"):
            continue
        uid = snap.reference.parent.parent.id if snap.reference.parent.parent else None
        if not uid:
            continue
        yield {
            "uid": uid,
            "spotify_refresh_token": data["spotify_refresh_token"],
            "spotify_user_id": data.get("spotify_user_id"),
            "consent_version": data.get("consent_version"),
        }


def write_weekly_report(uid: str, report: WeeklyListeningReport) -> str:
    """Idempotent: keyed on week_start_iso, so a re-run overwrites cleanly."""
    db = _client()
    doc_id = report.week_start_iso
    ref = (
        db.collection("users").document(uid)
        .collection(LISTENING_DATA_NAMESPACE["weekly_reports"]).document(doc_id)
    )
    ref.set(report.to_firestore_dict(), merge=False)
    return ref.id


def write_flagged_tracks(uid: str, tracks: Iterable[FlaggedTrack]) -> int:
    """Bulk-write flagged tracks under the user's activity_log_flags collection."""
    db = _client()
    coll = db.collection("users").document(uid).collection(
        LISTENING_DATA_NAMESPACE["flagged_tracks"]
    )
    n = 0
    for t in tracks:
        payload = t.to_firestore_dict()
        payload["source"] = "spotify_recently_played"
        coll.add(payload)
        n += 1
    return n


def delete_user_history(uid: str) -> dict[str, int]:
    """Server-side "delete my listening data" cleanup.

    Used both by the Android client's privacy panel and by the GDPR-style
    request flow described in the README's privacy section. Deletes
    everything under the three subcollections this package writes.
    """
    db = _client()
    counts = {"flagged_tracks": 0, "weekly_reports": 0, "user_dismissals": 0}
    for key, sub in LISTENING_DATA_NAMESPACE.items():
        coll = db.collection("users").document(uid).collection(sub)
        for snap in coll.stream():
            snap.reference.delete()
            counts[key] += 1
    return counts
