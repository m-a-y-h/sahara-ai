"""Firestore reader — turns raw activity subcollections into a [WeeklyActivitySnapshot].

The aggregator is the only piece that knows the on-disk Firestore schema.
Every other module in this package operates on the strongly-typed
[WeeklyActivitySnapshot] / [WeeklyFeatureVector] dataclasses, so swapping
storage backends (e.g. swapping Firestore for a Postgres warehouse) only
touches this file.

Subcollections this aggregator currently reads (all under ``users/{uid}/``):

  * ``sahara_lens_checkins``         — face FER results
  * ``sahara_voice_checkins``        — voice emotion results
  * ``chat_messages``                — sahara_ai message log (filter by week)
  * ``journal_entries``              — Android journal writes
  * ``sleep_logs``                   — Android self-report + Health Connect daily docs
  * ``activity_log_flags``           — sahara_listening flagged tracks
  * ``social_post_flags``            — sahara_social / bluesky-poc derived rows
  * ``steam_play_flags``             — flagged Steam plays (per-game minutes)
  * ``youtube_sub_flags``            — flagged YouTube subscriptions
  * ``screen_time_log``              — per-app daily minutes with cached
                                       severity from app_reputation lookups
  * ``recovery_points_log``          — gamified-tracker per-day points

Anything that isn't wired yet returns an empty summary — the snapshot's
``Optional`` fields handle missing sources cleanly.
"""

from __future__ import annotations

import logging
from datetime import date, datetime, timedelta, timezone
from typing import Iterable, Optional

from .features import (
    ChatSentimentSummary,
    DEFAULT_SLEEP_HOURS,
    FaceEmotionSummary,
    JournalSummary,
    ListeningMusicSummary,
    RecoveryCreditSummary,
    ScreenTimeSummary,
    SleepSource,
    SleepSummary,
    SocialPostsSummary,
    SteamGamesSummary,
    VoiceEmotionSummary,
    WeeklyActivitySnapshot,
    YouTubeSubsSummary,
)

logger = logging.getLogger("sahara_risk.aggregator")


# ---------------------------------------------------------------------------
# Firestore client (lazy import so tests don't need google.cloud)
# ---------------------------------------------------------------------------


def _firestore():
    from google.cloud import firestore  # type: ignore
    return firestore.Client()


def _in_range(ts, start: datetime, end: datetime) -> bool:
    if ts is None:
        return False
    if hasattr(ts, "to_datetime"):
        ts = ts.to_datetime()
    if isinstance(ts, str):
        try:
            ts = datetime.fromisoformat(ts.replace("Z", "+00:00"))
        except ValueError:
            return False
    if not isinstance(ts, datetime):
        return False
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)
    return start <= ts < end


# ---------------------------------------------------------------------------
# Per-source readers
# ---------------------------------------------------------------------------


def _read_lens(uid: str, start: datetime, end: datetime) -> Optional[FaceEmotionSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("sahara_lens_checkins")
    levels: list[float] = []
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        level = (d.get("level") or "neutral").lower()
        levels.append(_distress_from_level(level))
    if not levels:
        return None
    return FaceEmotionSummary(distress_levels_per_day=levels, capture_count=len(levels))


def _read_voice(uid: str, start: datetime, end: datetime) -> Optional[VoiceEmotionSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("sahara_voice_checkins")
    levels: list[float] = []
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        level = (d.get("level") or "neutral").lower()
        levels.append(_distress_from_level(level))
    if not levels:
        return None
    return VoiceEmotionSummary(distress_levels_per_day=levels, capture_count=len(levels))


def _read_chat(uid: str, start: datetime, end: datetime) -> Optional[ChatSentimentSummary]:
    db = _firestore()
    coll = db.collection("chat_messages")
    total = 0
    flagged = 0
    critical = 0
    risk_sum = 0.0
    for snap in coll.where("uid", "==", uid).stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        if d.get("role") != "assistant":
            continue
        total += 1
        risk_sum += float(d.get("riskLevel_score") or 0.0)
        if d.get("triggerCounselor"):
            flagged += 1
        if (d.get("riskLevel") or "").lower() == "critical":
            critical += 1
    if total == 0:
        return None
    return ChatSentimentSummary(
        total_messages=total,
        flagged_messages=flagged,
        critical_messages=critical,
        average_risk_score=risk_sum / total,
    )


def _read_journal(uid: str, start: datetime, end: datetime) -> Optional[JournalSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("journal_entries")
    n = 0
    user_mood_sum = 0.0
    nlp_mood_sum = 0.0
    flagged = 0
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        n += 1
        user_mood_sum += float(d.get("user_mood") or 0.5)
        nlp_mood_sum += float(d.get("nlp_mood") or 0.5)
        if d.get("flagged"):
            flagged += 1
    if n == 0:
        return None
    return JournalSummary(
        entry_count=n,
        user_stated_mood_average=user_mood_sum / n,
        nlp_inferred_mood_average=nlp_mood_sum / n,
        flagged_entries=flagged,
    )


def _summarise_sleep_records(
    records: Iterable[dict],
    week_start: date,
    six_month_avg: Optional[float],
) -> SleepSummary:
    """Turn source-tagged daily records into seven ordered nightly values."""
    week_end = week_start + timedelta(days=7)
    hours_by_day: dict[str, float] = {}
    source_by_day: dict[str, SleepSource] = {}
    for record in records:
        day = str(record.get("date") or "")
        try:
            parsed_day = date.fromisoformat(day)
        except ValueError:
            continue
        if not week_start <= parsed_day < week_end:
            continue
        hours = float(record.get("hours") or 0.0)
        if not 0.0 < hours <= 24.0:
            continue
        raw_source = str(record.get("source") or SleepSource.SELF_REPORTED.value)
        try:
            source = SleepSource(raw_source)
        except ValueError:
            source = SleepSource.SELF_REPORTED
        hours_by_day[day] = hours
        source_by_day[day] = source

    hours: list[float] = []
    sources: list[SleepSource] = []
    fallback_used = False
    for offset in range(7):
        day_key = (week_start + timedelta(days=offset)).isoformat()
        if day_key in hours_by_day:
            hours.append(hours_by_day[day_key])
            sources.append(source_by_day[day_key])
        elif six_month_avg is not None and six_month_avg > 0:
            hours.append(six_month_avg)
            sources.append(SleepSource.SIX_MONTH_AVERAGE)
            fallback_used = True
        else:
            hours.append(DEFAULT_SLEEP_HOURS)
            sources.append(SleepSource.DEFAULT)
            fallback_used = True
    return SleepSummary(
        hours_per_day=hours,
        source_per_day=sources,
        fallback_used=fallback_used,
        average_hours=sum(hours) / 7.0,
    )


def _read_sleep(uid: str, start: datetime, end: datetime, six_month_avg: Optional[float]) -> SleepSummary:
    """Always returns a summary, using stored sleep dates in their recorded week."""
    db = _firestore()
    coll = db.collection("users").document(uid).collection("sleep_logs")
    records: list[dict] = []
    for snap in coll.stream():
        d = snap.to_dict() or {}
        records.append(d)
    return _summarise_sleep_records(records, start.date(), six_month_avg)


def _read_listening(uid: str, start: datetime, end: datetime) -> Optional[ListeningMusicSummary]:
    db = _firestore()
    # Weekly report doc keyed by week_start_iso.
    week_id = start.isoformat(timespec="seconds")
    doc = (
        db.collection("users").document(uid)
        .collection("weekly_reports").document(week_id)
        .get()
    )
    if not doc.exists:
        return None
    d = doc.to_dict() or {}
    return ListeningMusicSummary(
        flagged_count=int(d.get("flagged_count") or 0),
        aggregate_score=float(d.get("aggregate_score") or 0.0),
        high_severity_count=int((d.get("severity_breakdown") or {}).get("high", 0)),
    )


def _read_social(uid: str, start: datetime, end: datetime) -> Optional[SocialPostsSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("social_post_flags")
    total = 0
    flagged = 0
    neg_sum = 0.0
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        total += int(d.get("analysed_count") or 1)
        flagged += int(d.get("flagged_count") or 0)
        neg_sum += float(d.get("negative_sentiment") or 0.0)
    if total == 0:
        return None
    return SocialPostsSummary(
        total_posts_analysed=total,
        flagged_posts=flagged,
        average_negative_sentiment=min(1.0, neg_sum / max(1, total)),
    )


def _read_steam(uid: str, start: datetime, end: datetime) -> Optional[SteamGamesSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("steam_play_flags")
    minutes = 0
    titles: set[str] = set()
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        minutes += int(d.get("minutes") or 0)
        title = d.get("title")
        if title:
            titles.add(str(title))
    if minutes == 0 and not titles:
        return None
    return SteamGamesSummary(flagged_play_minutes=minutes, distinct_flagged_titles=len(titles))


def _read_youtube(uid: str, start: datetime, end: datetime) -> Optional[YouTubeSubsSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("youtube_sub_flags")
    flagged = 0
    total = 0
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        flagged += int(d.get("flagged_count") or 0)
        total += int(d.get("total_count") or 0)
    if total == 0:
        return None
    return YouTubeSubsSummary(
        flagged_subscriptions=flagged,
        flag_ratio=flagged / total,
    )


def _read_screen_time(uid: str, start: datetime, end: datetime) -> Optional[ScreenTimeSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("screen_time_log")
    weighted = 0.0
    distinct: set[str] = set()
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("recordedAt"), start, end):
            continue
        minutes = float(d.get("minutes") or 0.0)
        severity = float(d.get("app_severity") or 0.0)
        if severity <= 0:
            continue
        weighted += minutes * severity
        if d.get("package_hash"):
            distinct.add(str(d["package_hash"]))
    if weighted == 0.0:
        return None
    return ScreenTimeSummary(weighted_bad_minutes=weighted, distinct_bad_apps=len(distinct))


def _read_recovery(uid: str, start: datetime, end: datetime) -> Optional[RecoveryCreditSummary]:
    db = _firestore()
    coll = db.collection("users").document(uid).collection("recovery_points_log")
    points = 0
    streak = 0
    for snap in coll.stream():
        d = snap.to_dict() or {}
        if not _in_range(d.get("createdAt"), start, end):
            continue
        points += int(d.get("points") or 0)
        streak = max(streak, int(d.get("streak_days") or 0))
    if points == 0 and streak == 0:
        return None
    return RecoveryCreditSummary(points_earned=points, streak_days=streak)


def _distress_from_level(level: str) -> float:
    return {
        "neutral":   0.0,
        "elevated":  0.5,
        "high":      1.0,
        "uncertain": 0.25,
    }.get(level, 0.0)


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------


def build_snapshot(
    user_id: str,
    week_start: datetime,
    week_end: datetime,
    six_month_sleep_average: Optional[float] = None,
) -> WeeklyActivitySnapshot:
    """Construct the WeeklyActivitySnapshot the model consumes.

    Each per-source read swallows errors so a single misconfigured
    subcollection doesn't blow up the whole weekly run; the corresponding
    field stays None and the model treats it as missing.
    """
    safe = _safe_call

    return WeeklyActivitySnapshot(
        user_id=user_id,
        week_start=week_start,
        week_end=week_end,
        face       = safe(_read_lens,        user_id, week_start, week_end),
        voice      = safe(_read_voice,       user_id, week_start, week_end),
        chat       = safe(_read_chat,        user_id, week_start, week_end),
        journal    = safe(_read_journal,     user_id, week_start, week_end),
        sleep      = safe(_read_sleep,       user_id, week_start, week_end, six_month_sleep_average),
        listening  = safe(_read_listening,   user_id, week_start, week_end),
        social     = safe(_read_social,      user_id, week_start, week_end),
        steam      = safe(_read_steam,       user_id, week_start, week_end),
        youtube    = safe(_read_youtube,     user_id, week_start, week_end),
        screen_time= safe(_read_screen_time, user_id, week_start, week_end),
        recovery   = safe(_read_recovery,    user_id, week_start, week_end),
    )


def _safe_call(fn, *args, **kwargs):
    try:
        return fn(*args, **kwargs)
    except Exception:
        logger.exception(f"sahara_risk source reader {fn.__name__} failed; treating as missing")
        return None
