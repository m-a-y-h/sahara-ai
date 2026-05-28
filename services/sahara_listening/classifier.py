"""Pure-Python track classifier.

Input: a normalised ``Track`` dict (whatever shape the upstream API returns,
mapped to a small canonical form). Output: a [TrackClassification] containing
the binary "is this flagged?" decision, the contributing reasons, and a
0–1 severity score the weekly aggregator sums.

The implementation is intentionally rule-based:

    * Reproducible — no ML model, no API key, easy to unit-test offline.
    * Auditable — every flag carries a human-readable reason a counselor or
      compliance reviewer can read directly.
    * Swappable — the same ``classify_track`` signature works whether the
      upstream source is Spotify, YouTube channel titles, or Last.fm.

If a future contributor wants to swap in a lyrics-based ML classifier they
can do so by adding a new ``LyricsClassifier`` and combining its score with
this one in [classify_track] — the function's contract stays the same.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Iterable, Mapping, Optional, Sequence

from .config import (
    DEFAULT_SEVERITY_WEIGHTS,
    DENY_LIST_GENRES,
    DENY_LIST_KEYWORDS_EN,
    DENY_LIST_KEYWORDS_UR,
    FLAG_THRESHOLD,
    SeverityWeights,
)







class FlagSeverity(str, Enum):
    """Discrete severity level the Android client renders."""

    NONE = "none"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"

    @classmethod
    def from_score(cls, score: float) -> "FlagSeverity":
        if score >= 0.70:
            return cls.HIGH
        if score >= 0.45:
            return cls.MEDIUM
        if score >= FLAG_THRESHOLD:
            return cls.LOW
        return cls.NONE


@dataclass
class FlaggedTrack:
    """A single flagged track row persisted to Firestore."""

    track_id: str
    name: str
    artist: str
    album: Optional[str] = None
    played_at_iso: Optional[str] = None
    spotify_uri: Optional[str] = None
    external_url: Optional[str] = None
    genres: list[str] = field(default_factory=list)
    flag_reasons: list[str] = field(default_factory=list)
    severity: FlagSeverity = FlagSeverity.NONE
    score: float = 0.0

    def to_firestore_dict(self) -> dict:
        return {
            "track_id": self.track_id,
            "name": self.name,
            "artist": self.artist,
            "album": self.album,
            "played_at": self.played_at_iso,
            "spotify_uri": self.spotify_uri,
            "external_url": self.external_url,
            "genres": list(self.genres),
            "flag_reasons": list(self.flag_reasons),
            "severity": self.severity.value,
            "score": round(self.score, 4),
        }


@dataclass
class TrackClassification:
    """Output of [classify_track] for a single input track."""

    score: float
    severity: FlagSeverity
    reasons: list[str]

    @property
    def is_flagged(self) -> bool:
        return self.score >= FLAG_THRESHOLD







def _compile(p: str) -> re.Pattern[str]:
    return re.compile(p, flags=re.IGNORECASE | re.UNICODE)


_COMPILED_EN = tuple(_compile(p) for p in DENY_LIST_KEYWORDS_EN)
_COMPILED_UR = tuple(_compile(p) for p in DENY_LIST_KEYWORDS_UR)






_SELF_HARM_HINTS = {
    "suicide", "kill myself", "kill yourself", "end my life", "hang",
    "self-harm", "self harm", "cutting myself", "blade", "overdose", "od",
}
_DRUG_HINTS = {
    "heroin", "cocaine", "crystal meth", "methamphetamine", "molly", "xanax",
    "percocet", "oxy", "oxycontin", "sizzurp", "codeine", "fent", "fentanyl",
    "dope", "smack", "gear",
}
_DEPRESSION_HINTS = {
    "worthless", "go on", "give up", "everything is dark", "nothing matters",
}

# Roman-Urdu / Urdu-script hit buckets — mirror the English categorisation so
# a track called "Khudkushi" carries the same severity as one called "Suicide".
# Urdu literals are tested against the raw hit, ASCII against the lowercase
# form. Stays in sync with the Sahara AI text protocol's keyword vocabulary.
_UR_SELF_HARM_HINTS = {
    "khudkushi", "jaan dena", "marna chahta", "marna chahti",
    "خودکشی",
}
_UR_DRUG_HINTS = {
    "chitta", "charas", "afeem", "nasha", "aiis", "ayis",
    "چٹا", "چرس", "افیون", "نشہ",
}
_UR_DEPRESSION_HINTS = {
    "barbaad", "gham", "judai",
}







def _matches_any(patterns: Iterable[re.Pattern[str]], text: str) -> list[str]:
    """Return the *match strings* (not the patterns) for every hit."""
    hits: list[str] = []
    for p in patterns:
        m = p.search(text)
        if m is not None:
            hits.append(m.group(0).lower())
    return hits


def _categorise_english_hit(hit: str) -> str:
    """Map a raw keyword match to its severity-weight category."""
    h = hit.lower()
    if any(needle in h for needle in _SELF_HARM_HINTS):
        return "title_keyword_self_harm"
    if any(needle in h for needle in _DRUG_HINTS):
        return "title_keyword_drug"
    if any(needle in h for needle in _DEPRESSION_HINTS):
        return "title_keyword_depression"


    return "title_keyword_depression"


def _categorise_urdu_hit(hit: str) -> str:
    """Roman-Urdu / Urdu-script counterpart to [_categorise_english_hit]."""
    lo = hit.lower()
    if any(n in lo or n in hit for n in _UR_SELF_HARM_HINTS):
        return "title_keyword_self_harm"
    if any(n in lo or n in hit for n in _UR_DRUG_HINTS):
        return "title_keyword_drug"
    if any(n in lo or n in hit for n in _UR_DEPRESSION_HINTS):
        return "title_keyword_depression"
    return "title_keyword_depression"


def classify_track(
    track: Mapping[str, object],
    weights: SeverityWeights = DEFAULT_SEVERITY_WEIGHTS,
) -> TrackClassification:
    """Score a single track. ``track`` is a dict with these (all optional) keys:

    * ``name``        — track title
    * ``album``       — album title
    * ``artist``      — primary artist name
    * ``genres``      — sequence of lowercase Spotify genre tags
    * ``valence``     — 0–1 mood-positivity (from /audio-features)
    * ``energy``      — 0–1 energy level (from /audio-features)
    """
    name = str(track.get("name") or "").strip()
    album = str(track.get("album") or "").strip()
    artist = str(track.get("artist") or "").strip()
    genres = tuple(str(g).lower() for g in (track.get("genres") or ()))
    valence = _safe_float(track.get("valence"))
    energy = _safe_float(track.get("energy"))

    score = 0.0
    reasons: list[str] = []
    seen_categories: set[str] = set()

    
    matched_genres: list[str] = []
    for g in genres:
        for deny in DENY_LIST_GENRES:
            if deny in g:
                matched_genres.append(g)
                break
    if matched_genres:
        
        
        score += weights.deny_list_genre
        reasons.append(
            "Genre on Sahara deny-list: " + ", ".join(sorted(set(matched_genres))[:3])
        )
        seen_categories.add("deny_list_genre")

    
    haystack = f"{name} {album}".lower()
    en_hits = _matches_any(_COMPILED_EN, haystack)
    for hit in en_hits:
        category = _categorise_english_hit(hit)
        
        if category in seen_categories:
            continue
        if category == "title_keyword_self_harm":
            score += weights.title_keyword_self_harm
        elif category == "title_keyword_drug":
            score += weights.title_keyword_drug
        else:
            score += weights.title_keyword_depression
        reasons.append(f"Title/album keyword ({category.replace('title_keyword_', '')}): '{hit}'")
        seen_categories.add(category)

    # Urdu hits use the same per-category weights as English hits so a single
    # khudkushi / chitta / nasha in a title triggers a flag on its own.
    ur_hits = _matches_any(_COMPILED_UR, haystack)
    for hit in ur_hits:
        category = _categorise_urdu_hit(hit)
        if category in seen_categories:
            continue
        if category == "title_keyword_self_harm":
            score += weights.title_keyword_self_harm
        elif category == "title_keyword_drug":
            score += weights.title_keyword_drug
        else:
            score += weights.title_keyword_depression
        reasons.append(
            f"Urdu concerning keyword ({category.replace('title_keyword_', '')}): '{hit}'"
        )
        seen_categories.add(category)

    
    
    
    
    
    if valence is not None and valence < 0.20:
        if energy is not None and energy < 0.30:
            score += weights.very_low_energy_and_valence
            reasons.append(f"Very low valence ({valence:.2f}) and energy ({energy:.2f})")
        else:
            score += weights.very_low_valence
            reasons.append(f"Very low valence ({valence:.2f})")

    score = min(score, 1.0)
    return TrackClassification(
        score=score,
        severity=FlagSeverity.from_score(score),
        reasons=reasons,
    )


def classify_tracks(
    tracks: Sequence[Mapping[str, object]],
    weights: SeverityWeights = DEFAULT_SEVERITY_WEIGHTS,
) -> list[tuple[Mapping[str, object], TrackClassification]]:
    """Run [classify_track] over a batch, returning aligned (track, result) pairs."""
    return [(t, classify_track(t, weights)) for t in tracks]


def _safe_float(value: object) -> Optional[float]:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None
