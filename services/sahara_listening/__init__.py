"""Sahara Listening — weekly Spotify listening risk classifier.

> POLICY WARNING — read this before shipping
> -------------------------------------------
> Spotify's Developer Policy section IV.5.b prohibits using Spotify data for
> "Inferring health, well-being or related conditions of users." This package
> implements exactly that pattern. Running it under a production Spotify
> Developer account risks SAHARA AI's API access being revoked.
>
> For the FYP/research prototype this code is gated behind the
> ``SAHARA_LISTENING_RESEARCH_MODE`` environment variable. The Android client
> only ever reads the *derived* flagged-track and weekly-report Firestore
> documents — never raw listening history — so the same UI also works against
> the policy-clean YouTube-subscription path documented in the top-level
> README.

The package is structured to mirror sahara_ai / sahara_lens / sahara_voice:

    config.py            deny-list genres, concerning-keyword patterns, thresholds
    classifier.py        pure-Python rule-based track classifier
    weekly_report.py     aggregate a week's worth of flagged tracks
    spotify_client.py    minimal Spotify Web API wrapper (PKCE refresh)
    modal_deploy.py      Modal scheduled function that runs every Sunday
    firestore_writer.py  shared writer used by the Modal job

The screening logic in ``classifier.py`` and ``weekly_report.py`` is pure
Python and pure-functional — easy to unit-test, easy to swap the data source
to YouTube subscriptions later.
"""

from .classifier import (
    FlagSeverity,
    FlaggedTrack,
    TrackClassification,
    classify_track,
)
from .config import (
    DENY_LIST_GENRES,
    DENY_LIST_KEYWORDS_EN,
    DENY_LIST_KEYWORDS_UR,
    FLAG_THRESHOLD,
    LISTENING_DATA_NAMESPACE,
)
from .weekly_report import (
    WeeklyListeningReport,
    aggregate_weekly_report,
)

__all__ = [
    "FlagSeverity",
    "FlaggedTrack",
    "TrackClassification",
    "classify_track",
    "DENY_LIST_GENRES",
    "DENY_LIST_KEYWORDS_EN",
    "DENY_LIST_KEYWORDS_UR",
    "FLAG_THRESHOLD",
    "LISTENING_DATA_NAMESPACE",
    "WeeklyListeningReport",
    "aggregate_weekly_report",
]
