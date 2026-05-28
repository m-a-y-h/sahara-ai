"""Weekly feature schema — input to the per-user risk model.

The aggregator in [aggregator.py] reads raw Firestore subcollections and
fills a [WeeklyActivitySnapshot]. [model.update_weekly] then turns that
snapshot into a [WeeklyFeatureVector] of per-source signals in `[0, 1]`,
normalises each one against the user's running baseline, and uses the
result to update the latent risk score.

Every field is optional / nullable because:

  * A user can skip any source for a week (no face capture, no voice note,
    no journal entries). A missing source contributes neutrally — it does
    not penalise the user.
  * Missing sleep nights receive a "soft" fallback: imported or user-entered
    records > 6-month average > 6h default. See [SLEEP_FALLBACK_REASONS].
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional


# ---------------------------------------------------------------------------
# Sleep fallback ladder
# ---------------------------------------------------------------------------

DEFAULT_SLEEP_HOURS: float = 6.0


class SleepSource(str, Enum):
    """Where this week's sleep hours came from. Logged for audit clarity."""

    SELF_REPORTED = "self_reported"            # user logged it themselves
    HEALTH_CONNECT = "health_connect"          # sleep session imported with permission
    ACTIGRAPHY = "actigraphy"                  # foreground phone-motion estimate
    SIX_MONTH_AVERAGE = "six_month_average"    # fallback when no signal this week
    DEFAULT = "default"                        # first-week fallback (6h hardcoded)


SLEEP_FALLBACK_REASONS: dict[SleepSource, str] = {
    SleepSource.SELF_REPORTED:      "user logged sleep this week",
    SleepSource.HEALTH_CONNECT:     "user imported a recorded Health Connect sleep session",
    SleepSource.ACTIGRAPHY:         "phone-motion estimate had enough sampled coverage",
    SleepSource.SIX_MONTH_AVERAGE:  "no fresh signal; fell back to the user's 6-month average",
    SleepSource.DEFAULT:            "no signal and no history; defaulted to 6 hours/night",
}


# ---------------------------------------------------------------------------
# Activity snapshot — one row per week per user
# ---------------------------------------------------------------------------


@dataclass
class FaceEmotionSummary:
    """Up to 7 entries — the camera locks out for 24h after each capture."""

    distress_levels_per_day: list[float] = field(default_factory=list)
    capture_count: int = 0      # how many days actually had a capture
    # Source flag so we can audit per-day vs per-week-aggregate inputs.
    derived_from: str = "sahara_lens"


@dataclass
class VoiceEmotionSummary:
    """Up to 7 entries — voice note also has a 24h lockout."""

    distress_levels_per_day: list[float] = field(default_factory=list)
    capture_count: int = 0
    derived_from: str = "sahara_voice"


@dataclass
class ChatSentimentSummary:
    """Sahara AI text-chat aggregate sentiment for the week."""

    total_messages: int = 0
    flagged_messages: int = 0
    critical_messages: int = 0  # risk_level == "critical"
    average_risk_score: float = 0.0  # 0..1 over all turns


@dataclass
class JournalSummary:
    """Up to 21 entries per week (3/day cap)."""

    entry_count: int = 0
    user_stated_mood_average: float = 0.5   # 0..1, user's own slider
    nlp_inferred_mood_average: float = 0.5  # 0..1, parsed from text
    flagged_entries: int = 0                # entries with concerning keywords


@dataclass
class SleepSummary:
    """7 entries required — a recorded value or fallback fills each day."""

    hours_per_day: list[float] = field(default_factory=list)
    source_per_day: list[SleepSource] = field(default_factory=list)
    fallback_used: bool = False
    average_hours: float = DEFAULT_SLEEP_HOURS

    @property
    def bad_nights(self) -> int:
        """Count of nights outside the healthy 7–9 hour window."""
        return sum(1 for h in self.hours_per_day if h < 7.0 or h > 9.0)


@dataclass
class ListeningMusicSummary:
    """Mirrors the sahara_listening weekly report severity / score."""

    flagged_count: int = 0
    aggregate_score: float = 0.0   # 0..1
    high_severity_count: int = 0


@dataclass
class SocialPostsSummary:
    """Bluesky + in-app community sentiment for the week."""

    total_posts_analysed: int = 0
    flagged_posts: int = 0
    average_negative_sentiment: float = 0.0   # 0..1


@dataclass
class SteamGamesSummary:
    """Reverse-searched harmful Steam game plays this week."""

    flagged_play_minutes: int = 0
    distinct_flagged_titles: int = 0


@dataclass
class YouTubeSubsSummary:
    """Subscribed channels classified negative by the reverse search."""

    flagged_subscriptions: int = 0
    flag_ratio: float = 0.0   # flagged / total


@dataclass
class ScreenTimeSummary:
    """Bad-app minutes weighted by the app_reputation hash severity."""

    weighted_bad_minutes: float = 0.0     # sum(minutes * app_severity)
    distinct_bad_apps: int = 0


@dataclass
class RecoveryCreditSummary:
    """Gamified-recovery counter-signal (subtracts from the weekly observation)."""

    points_earned: int = 0
    streak_days: int = 0


# ---------------------------------------------------------------------------
# Top-level container
# ---------------------------------------------------------------------------


@dataclass
class WeeklyActivitySnapshot:
    """All weekly inputs for a single user. Built by [aggregator.build_snapshot]."""

    user_id: str
    week_start: datetime
    week_end: datetime

    face: Optional[FaceEmotionSummary] = None
    voice: Optional[VoiceEmotionSummary] = None
    chat: Optional[ChatSentimentSummary] = None
    journal: Optional[JournalSummary] = None
    sleep: Optional[SleepSummary] = None
    listening: Optional[ListeningMusicSummary] = None
    social: Optional[SocialPostsSummary] = None
    steam: Optional[SteamGamesSummary] = None
    youtube: Optional[YouTubeSubsSummary] = None
    screen_time: Optional[ScreenTimeSummary] = None
    recovery: Optional[RecoveryCreditSummary] = None


# ---------------------------------------------------------------------------
# Feature vector — output of feature extraction
# ---------------------------------------------------------------------------


@dataclass
class WeeklyFeatureVector:
    """Per-source signals in `[0, 1]` ready for normalisation + weighting.

    Each value is "this week's evidence for this source"; missing sources
    are set to ``None`` so the model treats them as neutral (no
    contribution) rather than as 0 (perfect week).
    """

    face_emotion:    Optional[float] = None
    voice_emotion:   Optional[float] = None
    chat_sentiment:  Optional[float] = None
    journal_mood:    Optional[float] = None
    sleep_quality:   Optional[float] = None
    listening_music: Optional[float] = None
    social_posts:    Optional[float] = None
    steam_games:     Optional[float] = None
    youtube_subs:    Optional[float] = None
    screen_time:     Optional[float] = None
    recovery_credit: Optional[float] = None

    def to_dict(self) -> dict[str, Optional[float]]:
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
            "recovery_credit": self.recovery_credit,
        }


# ---------------------------------------------------------------------------
# Snapshot → feature vector
# ---------------------------------------------------------------------------


def _mean(xs: list[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0


def snapshot_to_features(snapshot: WeeklyActivitySnapshot) -> WeeklyFeatureVector:
    """Reduce each per-source summary to a single signal in `[0, 1]`."""
    v = WeeklyFeatureVector()

    if snapshot.face is not None:
        # Average distress over recorded days. We slightly penalise empty
        # days only by *not* awarding evidence, not by adding penalty.
        v.face_emotion = _mean(snapshot.face.distress_levels_per_day) if snapshot.face.capture_count > 0 else None

    if snapshot.voice is not None:
        v.voice_emotion = _mean(snapshot.voice.distress_levels_per_day) if snapshot.voice.capture_count > 0 else None

    if snapshot.chat is not None and snapshot.chat.total_messages > 0:
        # Combine the flag rate and the per-turn average risk score.
        flag_rate = snapshot.chat.flagged_messages / snapshot.chat.total_messages
        critical_rate = snapshot.chat.critical_messages / snapshot.chat.total_messages
        v.chat_sentiment = min(1.0, 0.4 * flag_rate + 0.4 * snapshot.chat.average_risk_score + 0.2 * critical_rate)

    if snapshot.journal is not None and snapshot.journal.entry_count > 0:
        # Take the worse of (user-stated, NLP-inferred) so a "happy" slider
        # can't mask alarming text. Mood is 0..1 *positive*, so 1 - mood = risk.
        worse_mood = min(snapshot.journal.user_stated_mood_average, snapshot.journal.nlp_inferred_mood_average)
        flagged_rate = snapshot.journal.flagged_entries / snapshot.journal.entry_count
        v.journal_mood = min(1.0, 0.7 * (1.0 - worse_mood) + 0.3 * flagged_rate)

    if snapshot.sleep is not None:
        # Bad-nights ratio over the 7-day window, gently penalised. 0 bad
        # nights → 0; 7 bad nights → 1.
        bad = snapshot.sleep.bad_nights
        v.sleep_quality = min(1.0, bad / 7.0)

    if snapshot.listening is not None:
        v.listening_music = max(0.0, min(1.0, snapshot.listening.aggregate_score))

    if snapshot.social is not None and snapshot.social.total_posts_analysed > 0:
        v.social_posts = max(0.0, min(1.0, snapshot.social.average_negative_sentiment))

    if snapshot.steam is not None:
        # 60 minutes of flagged play in a week ≈ 1.0; we cap there.
        v.steam_games = min(1.0, snapshot.steam.flagged_play_minutes / 60.0)

    if snapshot.youtube is not None:
        v.youtube_subs = max(0.0, min(1.0, snapshot.youtube.flag_ratio))

    if snapshot.screen_time is not None:
        # 600 weighted-bad-minutes (~85 min/day on harmful apps) ≈ 1.0.
        v.screen_time = min(1.0, snapshot.screen_time.weighted_bad_minutes / 600.0)

    if snapshot.recovery is not None:
        # Recovery credit is the counter-signal: more points / longer
        # streak → larger downward push on the score.
        per_point = min(1.0, snapshot.recovery.points_earned / 100.0)
        streak_bonus = min(0.5, snapshot.recovery.streak_days / 14.0)  # cap at 2 weeks
        v.recovery_credit = min(1.0, per_point + streak_bonus)

    return v
