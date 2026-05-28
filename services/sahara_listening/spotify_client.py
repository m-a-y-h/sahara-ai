"""Minimal Spotify Web API wrapper for the weekly listening fetch.

> Read the [POLICY WARNING] in ``sahara_listening/__init__.py`` before
> enabling this module in production. Spotify's developer terms prohibit
> using their data to infer user health/well-being.

What this module covers (all the API surface the weekly fetch needs):

    * ``recently_played(token, limit=50)``      → last 50 played tracks
    * ``track_audio_features(token, track_ids)`` → valence / energy / etc.
    * ``artist_genres(token, artist_ids)``        → comma-separated genres
    * ``refresh_access_token(refresh_token)``    → PKCE / authorization-code refresh

Persistent refresh tokens are out of scope for this file — the Modal cron
job in ``modal_deploy.py`` reads the refresh token from a Modal secret per
user (or from Firestore if the team later moves to a backend OAuth store).

The HTTP calls use ``requests`` because (a) it's already in the Modal image
for sahara_ai and (b) Spotify's endpoints are JSON with no need for async
streaming. We keep retries shallow — the Modal cron is idempotent at the
report level, so on transient failure we'd rather skip the week than retry
heroically and burn Spotify's rate limit budget.
"""

from __future__ import annotations

import logging
from typing import Iterable, Optional

logger = logging.getLogger("sahara_listening.spotify")


SPOTIFY_API_BASE = "https://api.spotify.com/v1"
SPOTIFY_ACCOUNTS_BASE = "https://accounts.spotify.com"





REQUIRED_SCOPES: tuple[str, ...] = (
    "user-read-recently-played",
    "user-top-read",
)


def refresh_access_token(
    refresh_token: str,
    client_id: str,
    client_secret: Optional[str] = None,
    timeout_seconds: float = 10.0,
) -> Optional[str]:
    """Exchange a refresh token for a fresh access token.

    Spotify supports two refresh modes:

      * **Authorization code with client secret** — for confidential clients
        (server-side OAuth). Pass ``client_secret``.
      * **PKCE** — for public clients (Android). The existing ConnectionsScreen
        uses PKCE; in that mode ``client_secret`` is omitted.

    Returns ``None`` on any failure so the caller can log + skip that user
    for the week without taking down the whole cron.
    """
    try:
        import requests  
    except ImportError:
        logger.warning("requests not installed; cannot refresh Spotify token.")
        return None

    payload = {
        "grant_type": "refresh_token",
        "refresh_token": refresh_token,
        "client_id": client_id,
    }
    auth = None
    if client_secret:
        auth = (client_id, client_secret)
        
        
        del payload["client_id"]

    try:
        resp = requests.post(
            f"{SPOTIFY_ACCOUNTS_BASE}/api/token",
            data=payload,
            auth=auth,
            timeout=timeout_seconds,
        )
    except requests.RequestException as exc:
        logger.warning(f"Spotify token refresh failed: {exc!r}")
        return None
    if resp.status_code != 200:
        logger.warning(f"Spotify token refresh HTTP {resp.status_code}: {resp.text[:200]}")
        return None
    return resp.json().get("access_token")


def recently_played(access_token: str, limit: int = 50) -> list[dict]:
    """Fetch the last ``limit`` (≤50) played tracks.

    Returns the raw 'items' list from the Spotify response — each entry has
    ``played_at`` and a nested ``track`` object. Use [normalize_recently_played]
    to flatten them into the dicts the classifier expects.
    """
    try:
        import requests
    except ImportError:
        return []

    try:
        resp = requests.get(
            f"{SPOTIFY_API_BASE}/me/player/recently-played",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"limit": min(limit, 50)},
            timeout=10.0,
        )
    except requests.RequestException as exc:
        logger.warning(f"Spotify recently_played failed: {exc!r}")
        return []
    if resp.status_code != 200:
        logger.warning(f"Spotify recently_played HTTP {resp.status_code}: {resp.text[:200]}")
        return []
    return resp.json().get("items", [])


def track_audio_features(access_token: str, track_ids: Iterable[str]) -> dict[str, dict]:
    """Batch-fetch audio features for up to 100 tracks; returns {id: features}."""
    ids = [t for t in track_ids if t][:100]
    if not ids:
        return {}
    try:
        import requests
    except ImportError:
        return {}
    try:
        resp = requests.get(
            f"{SPOTIFY_API_BASE}/audio-features",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"ids": ",".join(ids)},
            timeout=10.0,
        )
    except requests.RequestException as exc:
        logger.warning(f"Spotify audio-features failed: {exc!r}")
        return {}
    if resp.status_code != 200:
        logger.warning(f"Spotify audio-features HTTP {resp.status_code}: {resp.text[:200]}")
        return {}
    out: dict[str, dict] = {}
    for feat in resp.json().get("audio_features", []):
        if isinstance(feat, dict) and feat.get("id"):
            out[feat["id"]] = feat
    return out


def artist_genres(access_token: str, artist_ids: Iterable[str]) -> dict[str, list[str]]:
    """Batch-fetch artist genres for up to 50 ids; returns {id: [genres]}."""
    ids = [a for a in artist_ids if a][:50]
    if not ids:
        return {}
    try:
        import requests
    except ImportError:
        return {}
    try:
        resp = requests.get(
            f"{SPOTIFY_API_BASE}/artists",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"ids": ",".join(ids)},
            timeout=10.0,
        )
    except requests.RequestException as exc:
        logger.warning(f"Spotify artists failed: {exc!r}")
        return {}
    if resp.status_code != 200:
        logger.warning(f"Spotify artists HTTP {resp.status_code}: {resp.text[:200]}")
        return {}
    out: dict[str, list[str]] = {}
    for a in resp.json().get("artists", []):
        if isinstance(a, dict) and a.get("id"):
            out[a["id"]] = [str(g).lower() for g in (a.get("genres") or [])]
    return out







def normalize_recently_played(
    items: list[dict],
    audio_features: Optional[dict[str, dict]] = None,
    genres_by_artist: Optional[dict[str, list[str]]] = None,
) -> list[dict]:
    """Flatten Spotify ``recently-played`` items into the classifier shape.

    Drops malformed entries silently because the cron job's contract is
    "best-effort weekly summary", not "fail the whole report if Spotify
    served us one bad row."
    """
    audio_features = audio_features or {}
    genres_by_artist = genres_by_artist or {}
    out: list[dict] = []
    for item in items:
        track = (item or {}).get("track") or {}
        if not track.get("id"):
            continue
        artists = track.get("artists") or []
        primary = artists[0] if artists else {}
        primary_id = str(primary.get("id") or "")
        genres = list(genres_by_artist.get(primary_id, ()))
        af = audio_features.get(str(track.get("id")), {})
        out.append({
            "track_id": str(track.get("id")),
            "name": str(track.get("name") or ""),
            "album": str((track.get("album") or {}).get("name") or ""),
            "artist": str(primary.get("name") or ""),
            "artist_id": primary_id,
            "spotify_uri": str(track.get("uri") or ""),
            "external_url": str((track.get("external_urls") or {}).get("spotify") or ""),
            "played_at": item.get("played_at"),
            "genres": genres,
            "valence": af.get("valence"),
            "energy": af.get("energy"),
        })
    return out
