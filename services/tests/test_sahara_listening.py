"""Tests for the Sahara Listening classifier + weekly aggregator.

Pure-Python — no torch / no librosa / no network. The classifier is
designed to be exhaustively unit-testable so the rules behind a counselor
escalation can be audited offline.
"""

from __future__ import annotations

import unittest
from datetime import datetime, timezone







class TestTrackClassifier(unittest.TestCase):
    def test_benign_pop_song_is_not_flagged(self):
        from sahara_listening.classifier import classify_track, FlagSeverity

        result = classify_track({
            "name": "Sunflower",
            "artist": "Post Malone",
            "album": "Spider-Man: Into the Spider-Verse",
            "genres": ["dfw rap", "melodic rap", "pop"],
            "valence": 0.8,
            "energy": 0.7,
        })
        self.assertFalse(result.is_flagged)
        self.assertEqual(result.severity, FlagSeverity.NONE)
        self.assertEqual(result.reasons, [])

    def test_depressive_black_metal_genre_flags(self):
        from sahara_listening.classifier import classify_track, FlagSeverity

        result = classify_track({
            "name": "Untitled",
            "artist": "Generic DSBM",
            "genres": ["depressive black metal", "raw black metal"],
            "valence": 0.3,
            "energy": 0.5,
        })
        self.assertTrue(result.is_flagged)
        self.assertIn(result.severity, (FlagSeverity.LOW, FlagSeverity.MEDIUM, FlagSeverity.HIGH))
        self.assertTrue(any("deny-list" in r for r in result.reasons))

    def test_blackgaze_substring_match_catches_subgenre(self):
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "Untitled",
            "artist": "Blackgaze Band",
            "genres": ["atmospheric blackgaze"],
        })
        self.assertTrue(result.is_flagged)

    def test_self_harm_title_keyword_raises_to_high(self):
        from sahara_listening.classifier import classify_track, FlagSeverity

        result = classify_track({
            "name": "I want to kill myself tonight",
            "artist": "Someone",
            "genres": ["emo"],
            "valence": 0.10,
            "energy": 0.20,
        })
        self.assertTrue(result.is_flagged)
        self.assertEqual(result.severity, FlagSeverity.HIGH)
        
        self.assertGreaterEqual(len(result.reasons), 2)

    def test_drug_glamour_title_flags(self):
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "Heroin Junkie Anthem",
            "artist": "Tester",
            "genres": ["rock"],
        })
        self.assertTrue(result.is_flagged)
        self.assertTrue(any("drug" in r.lower() for r in result.reasons))

    def test_roman_urdu_chitta_in_title_flags(self):
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "Chitta ka Nasha",
            "artist": "Tester",
            "genres": ["hip hop"],
        })
        self.assertTrue(result.is_flagged)
        self.assertTrue(any("urdu" in r.lower() or "concerning" in r.lower() for r in result.reasons))

    def test_urdu_script_khudkushi_in_title_flags(self):
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "خودکشی",
            "artist": "Tester",
            "genres": ["sad pakistani"],
        })
        self.assertTrue(result.is_flagged)

    def test_very_low_valence_alone_is_not_enough(self):
        """A neutral title at low valence should *not* by itself flag the track.
        We rely on a combined signal to keep false positives low."""
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "Goodbye",
            "artist": "Pop Singer",
            "genres": ["pop"],
            "valence": 0.05,
            "energy": 0.40,   
        })
        
        self.assertFalse(result.is_flagged)

    def test_score_clamped_to_one(self):
        """Stacking multiple maxed-out signals should still produce score ≤ 1.0."""
        from sahara_listening.classifier import classify_track

        result = classify_track({
            "name": "Suicide overdose heroin Chitta",
            "artist": "Edge case",
            "genres": ["depressive black metal", "goregrind"],
            "valence": 0.05,
            "energy": 0.10,
        })
        self.assertLessEqual(result.score, 1.0)

    def test_unknown_fields_default_safely(self):
        from sahara_listening.classifier import classify_track

        result = classify_track({})   
        self.assertFalse(result.is_flagged)
        self.assertEqual(result.reasons, [])







class TestWeeklyAggregator(unittest.TestCase):
    def test_empty_week_produces_neutral_report(self):
        from sahara_listening.weekly_report import aggregate_weekly_report
        from sahara_listening.classifier import FlagSeverity

        week_start = datetime(2026, 1, 5, tzinfo=timezone.utc)
        report = aggregate_weekly_report(
            user_id="u1", tracks=[], week_start=week_start,
        )
        self.assertEqual(report.flagged_count, 0)
        self.assertEqual(report.total_tracks, 0)
        self.assertEqual(report.severity, FlagSeverity.NONE)
        self.assertEqual(report.aggregate_score, 0.0)
        self.assertEqual(report.week_start_iso, "2026-01-05T00:00:00+00:00")

    def test_one_high_severity_track_in_a_quiet_week_raises_low(self):
        """One bad track in a 30-track healthy week is LOW severity overall."""
        from sahara_listening.weekly_report import aggregate_weekly_report
        from sahara_listening.classifier import FlagSeverity

        tracks = [{"name": "Sunflower", "artist": "X", "genres": ["pop"], "valence": 0.7, "energy": 0.7}] * 29
        tracks.append({
            "name": "Suicide",
            "artist": "Bad",
            "genres": ["depressive black metal"],
            "valence": 0.10,
            "energy": 0.15,
        })
        report = aggregate_weekly_report("u1", tracks, datetime.now(timezone.utc))
        self.assertEqual(report.flagged_count, 1)
        
        self.assertEqual(report.severity, FlagSeverity.LOW)

    def test_many_concerning_tracks_raise_to_high(self):
        from sahara_listening.weekly_report import aggregate_weekly_report
        from sahara_listening.classifier import FlagSeverity

        tracks = [
            {"name": "Suicide", "artist": "Bad", "genres": ["depressive black metal"],
             "valence": 0.05, "energy": 0.10}
            for _ in range(10)
        ]
        report = aggregate_weekly_report("u1", tracks, datetime.now(timezone.utc))
        self.assertEqual(report.flagged_count, 10)
        self.assertEqual(report.severity, FlagSeverity.HIGH)

    def test_genre_counts_and_top_genres(self):
        from sahara_listening.weekly_report import aggregate_weekly_report

        tracks = (
            [{"name": "A", "artist": "X", "genres": ["pop"], "valence": 0.8, "energy": 0.5}] * 5 +
            [{"name": "B", "artist": "Y", "genres": ["doom metal"], "valence": 0.2, "energy": 0.2}] * 3 +
            [{"name": "C", "artist": "Z", "genres": ["hip hop", "pop"], "valence": 0.5, "energy": 0.5}] * 2
        )
        report = aggregate_weekly_report("u1", tracks, datetime.now(timezone.utc))
        top = dict(report.top_genres)
        self.assertEqual(top["pop"], 7)
        self.assertEqual(top["doom metal"], 3)
        self.assertEqual(top["hip hop"], 2)

    def test_firestore_dict_is_json_serialisable(self):
        from sahara_listening.weekly_report import aggregate_weekly_report
        import json

        tracks = [{"name": "X", "artist": "Y", "genres": ["pop"], "valence": 0.5, "energy": 0.5}]
        report = aggregate_weekly_report("u1", tracks, datetime.now(timezone.utc))
        d = report.to_firestore_dict()
        json.dumps(d)  

    def test_current_week_window_is_monday_to_monday(self):
        from sahara_listening.weekly_report import current_week_window

        
        now = datetime(2026, 1, 7, 12, 34, tzinfo=timezone.utc)
        start, end = current_week_window(now)
        self.assertEqual(start.weekday(), 0)
        self.assertEqual(start.isoformat(), "2026-01-05T00:00:00+00:00")
        self.assertEqual(end.isoformat(),   "2026-01-12T00:00:00+00:00")







class TestSpotifyNormalisation(unittest.TestCase):
    def test_recently_played_normalises_into_classifier_shape(self):
        from sahara_listening.spotify_client import normalize_recently_played

        items = [
            {
                "played_at": "2026-01-05T14:21:00Z",
                "track": {
                    "id": "track123",
                    "name": "Suicide",
                    "uri": "spotify:track:track123",
                    "external_urls": {"spotify": "https://open.spotify.com/track/track123"},
                    "album": {"name": "Some Album"},
                    "artists": [{"id": "artist123", "name": "Bad Artist"}],
                },
            },
            
            {"played_at": "2026-01-05T14:22:00Z", "track": None},
        ]
        af = {"track123": {"valence": 0.05, "energy": 0.10}}
        genres = {"artist123": ["depressive black metal"]}

        out = normalize_recently_played(items, audio_features=af, genres_by_artist=genres)
        self.assertEqual(len(out), 1)
        self.assertEqual(out[0]["name"], "Suicide")
        self.assertEqual(out[0]["valence"], 0.05)
        self.assertEqual(out[0]["genres"], ["depressive black metal"])
        self.assertEqual(out[0]["spotify_uri"], "spotify:track:track123")


if __name__ == "__main__":
    unittest.main()
