"""Tests for the per-user unsupervised risk model.

Pure-Python — no Firestore, no Modal. Exercises the EWMA update, the
per-user z-score normalisation, monitoring-window arithmetic, the
DAST → initial risk lookup, and the snapshot → feature vector reducer.
"""

from __future__ import annotations

import unittest
from datetime import date, datetime, timedelta, timezone


# ---------------------------------------------------------------------------
# Config + monitoring window
# ---------------------------------------------------------------------------


class TestMonitoringWindow(unittest.TestCase):
    def test_window_is_six_months_starting_one_week_after_dast(self):
        from sahara_risk.monitoring import build_monitoring_period

        dast_done = datetime(2026, 1, 5, 10, 30, tzinfo=timezone.utc)
        period = build_monitoring_period("user1", dast_done)
        self.assertEqual(period.monitoring_starts_at, dast_done + timedelta(weeks=1))
        self.assertEqual(period.monitoring_ends_at - period.monitoring_starts_at, timedelta(weeks=26))
        self.assertEqual(period.duration_weeks, 26)

    def test_weeks_remaining_decays_monotonically(self):
        from sahara_risk.monitoring import build_monitoring_period, weeks_remaining

        dast_done = datetime(2026, 1, 5, tzinfo=timezone.utc)
        period = build_monitoring_period("user1", dast_done)
        self.assertEqual(weeks_remaining(period, now=dast_done + timedelta(weeks=0)), 26)
        self.assertEqual(weeks_remaining(period, now=dast_done + timedelta(weeks=4)), 23)
        self.assertEqual(weeks_remaining(period, now=dast_done + timedelta(weeks=27)), 0)  # at end
        self.assertEqual(weeks_remaining(period, now=dast_done + timedelta(weeks=200)), 0)

    def test_is_inside_monitoring_bounds(self):
        from sahara_risk.monitoring import build_monitoring_period, is_inside_monitoring

        dast_done = datetime(2026, 1, 5, tzinfo=timezone.utc)
        period = build_monitoring_period("user1", dast_done)
        # Before start
        self.assertFalse(is_inside_monitoring(period, now=dast_done + timedelta(days=3)))
        # Right at start
        self.assertTrue(is_inside_monitoring(period, now=period.monitoring_starts_at))
        # Mid-window
        self.assertTrue(is_inside_monitoring(period, now=period.monitoring_starts_at + timedelta(weeks=13)))
        # After end
        self.assertFalse(is_inside_monitoring(period, now=period.monitoring_ends_at))


class TestDastInitialRisk(unittest.TestCase):
    def test_zero_score_floors_low_and_max_score_floors_high(self):
        from sahara_risk.config import initial_risk_from_dast

        self.assertLess(initial_risk_from_dast(0), 0.15)
        self.assertGreater(initial_risk_from_dast(10), 0.85)
        # monotone
        for i in range(10):
            self.assertLessEqual(
                initial_risk_from_dast(i),
                initial_risk_from_dast(i + 1),
            )

    def test_out_of_range_clamps_safely(self):
        from sahara_risk.config import initial_risk_from_dast

        self.assertEqual(initial_risk_from_dast(-5), initial_risk_from_dast(0))
        self.assertEqual(initial_risk_from_dast(99), initial_risk_from_dast(10))


# ---------------------------------------------------------------------------
# Welford + z-score normalisation
# ---------------------------------------------------------------------------


class TestRunningStats(unittest.TestCase):
    def test_welford_matches_naive_mean_and_variance(self):
        from sahara_risk.model import FeatureRunningStats

        values = [0.10, 0.40, 0.55, 0.30, 0.25, 0.80, 0.15]
        s = FeatureRunningStats()
        for v in values:
            s.update(v)
        self.assertAlmostEqual(s.mean, sum(values) / len(values), places=6)
        # The risk profile persists population variance over its observed weeks.
        expected = sum((v - s.mean) ** 2 for v in values) / len(values)
        self.assertAlmostEqual(s.variance, expected, places=6)


# ---------------------------------------------------------------------------
# Weekly update — the meat
# ---------------------------------------------------------------------------


def _make_profile(dast_score: int = 4):
    from sahara_risk.model import initialise_profile_from_dast

    starts = datetime(2026, 1, 12, tzinfo=timezone.utc)
    return initialise_profile_from_dast("user1", dast_score, starts)


def _make_features(**kwargs):
    from sahara_risk.features import WeeklyFeatureVector

    return WeeklyFeatureVector(**kwargs)


class TestWeeklyUpdate(unittest.TestCase):

    def test_worked_example_matches_expected_week_one_and_two_scores(self):
        from sahara_risk.model import update_weekly

        profile = _make_profile(dast_score=4)

        _, week1 = update_weekly(
            profile,
            _make_features(face_emotion=0.30),
            "2026-01-19T00:00:00+00:00",
        )
        self.assertAlmostEqual(week1.observation, 0.30, places=6)
        self.assertAlmostEqual(week1.new_risk_score, 0.408, places=6)

        _, week2 = update_weekly(
            profile,
            _make_features(face_emotion=0.50, voice_emotion=0.20),
            "2026-01-26T00:00:00+00:00",
        )
        self.assertAlmostEqual(week2.feature_anomalies["face_emotion"], 0.75, places=6)
        self.assertAlmostEqual(week2.feature_anomalies["voice_emotion"], 0.20, places=6)
        self.assertAlmostEqual(week2.observation, 0.553571, places=6)
        self.assertAlmostEqual(week2.new_risk_score, 0.422557, places=6)

    def test_first_week_score_is_anchored_close_to_initial_score(self):
        from sahara_risk.model import update_weekly

        profile = _make_profile(dast_score=4)
        original = profile.current_risk_score
        # All-zero week (everything safe)
        features = _make_features(
            face_emotion=0.10, voice_emotion=0.10, chat_sentiment=0.10,
            journal_mood=0.10, sleep_quality=0.10, listening_music=0.10,
            social_posts=0.10, steam_games=0.10, youtube_subs=0.10,
            screen_time=0.10, recovery_credit=0.0,
        )
        _, update = update_weekly(profile, features, "2026-01-19T00:00:00+00:00")
        # alpha=0.90 in week 1 means the score moves at most 10 % of the way
        # toward the observation. With all-zero z-scores (first week, std=0,
        # _zscore_to_unit returns the raw value), observation ≈ 0.10. The
        # delta is therefore at most about 0.034.
        self.assertAlmostEqual(update.alpha, 0.90, places=2)
        self.assertGreaterEqual(profile.current_risk_score, original - 0.10)
        self.assertLessEqual(profile.current_risk_score, original + 0.15)

    def test_per_week_rise_is_clamped(self):
        from sahara_risk.config import MAX_WEEKLY_RISE
        from sahara_risk.model import update_weekly

        # Walk the user forward a few weeks so alpha is lower (more reactive).
        profile = _make_profile(dast_score=2)   # initial ~0.22
        # Build up a baseline of low values so a sudden bad week reads
        # very anomalous.
        low = _make_features(
            face_emotion=0.0, voice_emotion=0.0, chat_sentiment=0.0,
            journal_mood=0.0, sleep_quality=0.0, listening_music=0.0,
            social_posts=0.0, steam_games=0.0, youtube_subs=0.0,
            screen_time=0.0,
        )
        for w in range(1, 14):  # walk into the α=0.60 regime
            update_weekly(profile, low, f"week-{w}")
        before = profile.current_risk_score
        # Now slam the user with a maxed-out week
        bad = _make_features(
            face_emotion=1.0, voice_emotion=1.0, chat_sentiment=1.0,
            journal_mood=1.0, sleep_quality=1.0, listening_music=1.0,
            social_posts=1.0, steam_games=1.0, youtube_subs=1.0,
            screen_time=1.0,
        )
        update_weekly(profile, bad, "week-14")
        self.assertLessEqual(
            profile.current_risk_score - before,
            MAX_WEEKLY_RISE + 1e-6,
            "score should never rise more than MAX_WEEKLY_RISE in a single week",
        )

    def test_recovery_credit_reduces_observation(self):
        from sahara_risk.model import update_weekly

        profile = _make_profile(dast_score=5)
        baseline = _make_features(
            chat_sentiment=0.5, journal_mood=0.5, sleep_quality=0.5,
        )
        no_recovery_profile = _make_profile(dast_score=5)
        update_weekly(no_recovery_profile, baseline, "w1")

        with_recovery_profile = _make_profile(dast_score=5)
        recovery = _make_features(
            chat_sentiment=0.5, journal_mood=0.5, sleep_quality=0.5,
            recovery_credit=1.0,
        )
        update_weekly(with_recovery_profile, recovery, "w1")
        self.assertLessEqual(
            with_recovery_profile.current_risk_score,
            no_recovery_profile.current_risk_score,
            "a strong recovery week should leave the score lower than the same week without it",
        )

    def test_no_sources_keeps_score_steady(self):
        from sahara_risk.model import update_weekly

        profile = _make_profile(dast_score=4)
        before = profile.current_risk_score
        empty = _make_features()  # all None
        _, update = update_weekly(profile, empty, "w1")
        self.assertAlmostEqual(profile.current_risk_score, before)
        self.assertIn("no weekly evidence", " ".join(update.reasons))

    def test_score_stays_in_unit_interval(self):
        from sahara_risk.model import update_weekly

        profile = _make_profile(dast_score=10)  # already near 0.95
        # Throw maxed-out everything for many weeks
        bad = _make_features(
            face_emotion=1.0, voice_emotion=1.0, chat_sentiment=1.0,
            journal_mood=1.0, sleep_quality=1.0, listening_music=1.0,
            social_posts=1.0, steam_games=1.0, youtube_subs=1.0,
            screen_time=1.0,
        )
        for w in range(1, 30):
            update_weekly(profile, bad, f"w{w}")
            self.assertGreaterEqual(profile.current_risk_score, 0.0)
            self.assertLessEqual(profile.current_risk_score, 1.0)


# ---------------------------------------------------------------------------
# Snapshot → feature vector reducer
# ---------------------------------------------------------------------------


class TestSnapshotReducer(unittest.TestCase):
    def test_empty_snapshot_yields_all_none_signals(self):
        from sahara_risk.features import WeeklyActivitySnapshot, snapshot_to_features

        snap = WeeklyActivitySnapshot(
            user_id="u1",
            week_start=datetime(2026, 1, 12, tzinfo=timezone.utc),
            week_end=datetime(2026, 1, 19, tzinfo=timezone.utc),
        )
        v = snapshot_to_features(snap)
        for key, val in v.to_dict().items():
            self.assertIsNone(val, f"expected {key} None for empty snapshot")

    def test_journal_takes_worse_of_user_vs_nlp_mood(self):
        from sahara_risk.features import (
            JournalSummary,
            WeeklyActivitySnapshot,
            snapshot_to_features,
        )

        snap = WeeklyActivitySnapshot(
            user_id="u1",
            week_start=datetime(2026, 1, 12, tzinfo=timezone.utc),
            week_end=datetime(2026, 1, 19, tzinfo=timezone.utc),
            journal=JournalSummary(
                entry_count=10,
                user_stated_mood_average=0.90,    # "I'm great"
                nlp_inferred_mood_average=0.20,   # text says otherwise
                flagged_entries=2,
            ),
        )
        v = snapshot_to_features(snap)
        # 0.7 * (1 - 0.20) + 0.3 * (2/10) = 0.56 + 0.06 = 0.62
        self.assertAlmostEqual(v.journal_mood, 0.62, places=2)

    def test_sleep_quality_is_bad_nights_ratio(self):
        from sahara_risk.features import (
            SleepSource,
            SleepSummary,
            WeeklyActivitySnapshot,
            snapshot_to_features,
        )

        # 3 bad nights out of 7 → 3/7 ≈ 0.43
        snap = WeeklyActivitySnapshot(
            user_id="u1",
            week_start=datetime(2026, 1, 12, tzinfo=timezone.utc),
            week_end=datetime(2026, 1, 19, tzinfo=timezone.utc),
            sleep=SleepSummary(
                hours_per_day=[6.0, 5.0, 8.0, 8.0, 4.0, 7.5, 7.0],
                source_per_day=[SleepSource.SELF_REPORTED] * 7,
            ),
        )
        v = snapshot_to_features(snap)
        self.assertAlmostEqual(v.sleep_quality, 3 / 7, places=2)


class TestSleepAggregation(unittest.TestCase):
    def test_daily_sleep_records_are_ordered_by_wake_date_and_source(self):
        from sahara_risk.aggregator import _summarise_sleep_records
        from sahara_risk.features import SleepSource

        records = [
            {"date": "2026-01-14", "hours": 7.5, "source": "health_connect"},
            {"date": "2026-01-12", "hours": 8.0, "source": "self_reported"},
            {"date": "2026-01-11", "hours": 20.0, "source": "self_reported"},
        ]
        summary = _summarise_sleep_records(records, date(2026, 1, 12), 7.0)

        self.assertEqual(summary.hours_per_day[:3], [8.0, 7.0, 7.5])
        self.assertEqual(summary.source_per_day[:3], [
            SleepSource.SELF_REPORTED,
            SleepSource.SIX_MONTH_AVERAGE,
            SleepSource.HEALTH_CONNECT,
        ])
        self.assertTrue(summary.fallback_used)

    def test_invalid_duration_does_not_enter_weekly_summary(self):
        from sahara_risk.aggregator import _summarise_sleep_records
        from sahara_risk.features import SleepSource

        summary = _summarise_sleep_records(
            [{"date": "2026-01-12", "hours": 0, "source": "health_connect"}],
            date(2026, 1, 12),
            None,
        )

        self.assertEqual(summary.hours_per_day[0], 6.0)
        self.assertEqual(summary.source_per_day[0], SleepSource.DEFAULT)

    def test_automatic_sources_are_audited_in_weekly_summary(self):
        from sahara_risk.aggregator import _summarise_sleep_records
        from sahara_risk.features import SleepSource

        summary = _summarise_sleep_records(
            [
                {"date": "2026-01-12", "hours": 7.25, "source": "actigraphy"},
                {"date": "2026-01-13", "hours": 6.75, "source": "six_month_average"},
                {"date": "2026-01-14", "hours": 6.0, "source": "default"},
            ],
            date(2026, 1, 12),
            None,
        )

        self.assertEqual(summary.source_per_day[:3], [
            SleepSource.ACTIGRAPHY,
            SleepSource.SIX_MONTH_AVERAGE,
            SleepSource.DEFAULT,
        ])


class TestCumulativeReport(unittest.TestCase):
    def test_cumulative_report_summarises_trajectory_and_sources(self):
        from sahara_risk.cumulative_report import generate_cumulative_report

        profile = _make_profile(dast_score=4)
        profile.current_risk_score = 0.62
        history = [
            {
                "week_index": 0,
                "new_risk_score": 0.42,
                "feature_contributions": {},
            },
            {
                "week_index": 1,
                "new_risk_score": 0.44,
                "recovery_credit": 0.02,
                "feature_contributions": {"face_emotion": 0.10},
            },
            {
                "week_index": 2,
                "new_risk_score": 0.62,
                "recovery_credit": 0.04,
                "feature_contributions": {"face_emotion": 0.20, "voice_emotion": 0.05},
            },
            {
                "week_index": 3,
                "new_risk_score": 0.62,
                "feature_contributions": {},
            },
        ]

        report = generate_cumulative_report(
            profile,
            history,
            monitoring_ends_at=profile.monitoring_starts_at + timedelta(weeks=26),
            now=datetime(2026, 7, 13, tzinfo=timezone.utc),
        )

        self.assertEqual(report.risk_trajectory, [0.44, 0.62, 0.62])
        self.assertEqual(report.completed_weeks, 3)
        self.assertEqual(report.weeks_in_severity["moderate"], 1)
        self.assertEqual(report.weeks_in_severity["substantial"], 2)
        self.assertEqual(report.weeks_with_no_evidence, 1)
        self.assertAlmostEqual(report.total_recovery_credit, 0.06)
        self.assertAlmostEqual(
            report.feature_source_summary["face_emotion"]["mean_contribution"],
            0.15,
        )
        self.assertFalse(report.acknowledged)

    def test_reset_rules_preserve_chat_progress_and_reports(self):
        from sahara_risk.cumulative_report import must_preserve, should_reset

        self.assertTrue(should_reset("sahara_lens_checkins"))
        self.assertTrue(should_reset("risk_profile"))
        self.assertTrue(must_preserve("chat_messages"))
        self.assertTrue(must_preserve("recovery_points_log"))
        self.assertTrue(must_preserve("cumulative_reports"))
        self.assertFalse(should_reset("chat_messages"))
        self.assertFalse(should_reset("recovery_points_log"))


if __name__ == "__main__":
    unittest.main()
