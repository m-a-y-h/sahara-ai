"""Aggregate a week's worth of classified tracks into a WeeklyListeningReport.

The aggregator is pure-functional so it's trivially unit-testable and so the
same logic can run client-side or server-side without changes.

The output is serialised to Firestore at
``users/{uid}/weekly_reports/{week_start_iso}``. The Android client lists
those documents and renders the dashboard popup + the WeeklyReportScreen
detail view from them — the *raw* listening history is never persisted.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Iterable, Mapping, Optional, Sequence

from .classifier import (
    FlaggedTrack,
    FlagSeverity,
    TrackClassification,
    classify_track,
)
from .config import (
    WEEKLY_HIGH_THRESHOLD,
    WEEKLY_MEDIUM_THRESHOLD,
)


@dataclass
class WeeklyListeningReport:
    """A single weekly report, persisted as one Firestore document."""

    user_id: str
    week_start_iso: str      
    week_end_iso: str
    total_tracks: int
    flagged_tracks: list[FlaggedTrack] = field(default_factory=list)
    severity_breakdown: dict[str, int] = field(default_factory=dict)  
    top_genres: list[tuple[str, int]] = field(default_factory=list)   
    severity: FlagSeverity = FlagSeverity.NONE
    aggregate_score: float = 0.0
    generated_at_iso: Optional[str] = None
    model_version: str = "rules-1.0"

    @property
    def flagged_count(self) -> int:
        return len(self.flagged_tracks)

    def to_firestore_dict(self) -> dict:
        return {
            "user_id": self.user_id,
            "week_start": self.week_start_iso,
            "week_end": self.week_end_iso,
            "total_tracks": self.total_tracks,
            "flagged_count": self.flagged_count,
            "flagged_tracks": [t.to_firestore_dict() for t in self.flagged_tracks],
            "severity_breakdown": dict(self.severity_breakdown),
            "top_genres": [{"genre": g, "count": c} for g, c in self.top_genres],
            "severity": self.severity.value,
            "aggregate_score": round(self.aggregate_score, 4),
            "generated_at": self.generated_at_iso,
            "model_version": self.model_version,
        }







def aggregate_weekly_report(
    user_id: str,
    tracks: Sequence[Mapping[str, object]],
    week_start: datetime,
    week_end: Optional[datetime] = None,
    *,
    generated_at: Optional[datetime] = None,
) -> WeeklyListeningReport:
    """Build a [WeeklyListeningReport] from a list of raw track dicts.

    ``tracks`` is whatever the upstream fetcher returned — the same shape
    [classify_track] expects. ``week_start`` defines the report's window;
    [aggregate_weekly_report] does not filter on played_at, that's the
    fetcher's job.
    """
    week_end = week_end or (week_start + timedelta(days=7))
    generated_at = generated_at or datetime.now(timezone.utc)

    flagged: list[FlaggedTrack] = []
    severity_breakdown: dict[str, int] = {s.value: 0 for s in FlagSeverity}
    genre_counts: dict[str, int] = {}
    aggregate_score_sum = 0.0

    for track in tracks:
        result: TrackClassification = classify_track(track)
        severity_breakdown[result.severity.value] += 1
        aggregate_score_sum += result.score
        for g in (track.get("genres") or ()):
            g = str(g).lower()
            if not g:
                continue
            genre_counts[g] = genre_counts.get(g, 0) + 1
        if result.is_flagged:
            flagged.append(
                FlaggedTrack(
                    track_id=str(track.get("track_id") or track.get("id") or ""),
                    name=str(track.get("name") or "unknown"),
                    artist=str(track.get("artist") or "unknown"),
                    album=str(track.get("album") or "") or None,
                    played_at_iso=_iso_or_none(track.get("played_at")),
                    spotify_uri=str(track.get("spotify_uri") or "") or None,
                    external_url=str(track.get("external_url") or "") or None,
                    genres=[str(g).lower() for g in (track.get("genres") or ())],
                    flag_reasons=result.reasons,
                    severity=result.severity,
                    score=result.score,
                )
            )

    total_tracks = max(len(tracks), 0)
    
    mean_score = (aggregate_score_sum / total_tracks) if total_tracks > 0 else 0.0
    if mean_score >= WEEKLY_HIGH_THRESHOLD:
        week_severity = FlagSeverity.HIGH
    elif mean_score >= WEEKLY_MEDIUM_THRESHOLD:
        week_severity = FlagSeverity.MEDIUM
    elif flagged:
        week_severity = FlagSeverity.LOW
    else:
        week_severity = FlagSeverity.NONE

    top_genres = sorted(genre_counts.items(), key=lambda kv: kv[1], reverse=True)[:6]

    return WeeklyListeningReport(
        user_id=user_id,
        week_start_iso=_to_iso(week_start),
        week_end_iso=_to_iso(week_end),
        total_tracks=total_tracks,
        flagged_tracks=flagged,
        severity_breakdown=severity_breakdown,
        top_genres=top_genres,
        severity=week_severity,
        aggregate_score=mean_score,
        generated_at_iso=_to_iso(generated_at),
    )







def _to_iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat(timespec="seconds")


def _iso_or_none(v: object) -> Optional[str]:
    if v is None:
        return None
    if isinstance(v, datetime):
        return _to_iso(v)
    if isinstance(v, str) and v:
        return v
    return None


def current_week_window(now: Optional[datetime] = None) -> tuple[datetime, datetime]:
    """Return the (Monday-00:00 UTC, next Monday-00:00 UTC) window for ``now``."""
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    monday = now - timedelta(days=now.weekday())
    monday = monday.replace(hour=0, minute=0, second=0, microsecond=0)
    return monday, monday + timedelta(days=7)
