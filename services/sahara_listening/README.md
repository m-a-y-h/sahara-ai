# Sahara Listening — Weekly Spotify listening risk classifier

Run the commands in this document from the repository's `services/` directory.

> ⚠ **POLICY WARNING — read first**
>
> **Spotify's Developer Policy IV.5.b prohibits using Spotify data for
> "Inferring health, well-being or related conditions of users."** This
> package implements exactly that pattern. Shipping it under a production
> Spotify Developer account risks SAHARA AI's API access being revoked.
>
> For the FYP / research prototype the entire pipeline is gated behind the
> ``SAHARA_LISTENING_RESEARCH_MODE`` env var so the Modal cron will refuse
> to run unless someone explicitly opts in.
>
> The Android client only reads the *derived* `weekly_reports` and
> `activity_log_flags` Firestore documents — never raw listening history —
> so the same UI works against the policy-clean YouTube subscription path
> already documented in the top-level README.

## Package layout

```
sahara_listening/
├── __init__.py             public surface, with the policy warning above
├── config.py               deny-list genres + keywords + severity weights
├── classifier.py           pure-Python rule-based track classifier (tested)
├── weekly_report.py        aggregator: tracks → WeeklyListeningReport (tested)
├── spotify_client.py       minimal Spotify Web API wrapper
├── firestore_writer.py     reads opted-in users + writes report / flagged rows
├── modal_deploy.py         Modal cron (Sundays 18:00 UTC), gated behind env flag
└── README.md
```

## What gets flagged

The classifier looks at four signals per track:

1. **Genre deny-list** — substring match against the artist's Spotify
   genres. Catches families like *depressive black metal*, *funeral doom*,
   *blackgaze*, *witch house*, *goregrind*, *darkwave*. Not all heavy music —
   only subgenres where the lyric corpus is dominated by self-harm /
   severe-depression / drug-glamourising themes.
2. **Title / album keyword** (English) — explicit terms like `suicide`,
   `kill myself`, `overdose`, `heroin`, `xanax`, `oxycontin`, plus
   depression markers like `worthless`, `nothing matters`.
3. **Title / album keyword** (Roman Urdu / Urdu) — Pakistani-youth-specific
   terms shared with the Sahara AI text protocol: `khudkushi`, `chitta`,
   `charas`, `afeem`, `nasha`, `barbaad`, `gham`, `judai`, plus Urdu-script
   variants `خودکشی`, `چٹا`, etc.
4. **Audio features** — Spotify's `/audio-features` endpoint returns
   valence (0–1 mood) and energy (0–1). Very low valence (< 0.20) bumps
   the score; low-valence + low-energy together bumps it further.

Each signal contributes to a 0–1 severity score. Tracks above 0.40 are
considered flagged. Severity buckets (`LOW` / `MEDIUM` / `HIGH`) are
exposed to the Android client for icon / colour selection.

## Firestore schema

```
users/{uid}/
├── integrations/spotify           ← consent + refresh token (written by app)
│   ├── listening_analysis_opt_in: bool
│   ├── spotify_refresh_token: string
│   ├── consent_version: string
│   └── …
├── activity_log_flags/{auto-id}   ← one row per flagged track this week
│   ├── track_id, name, artist, album, genres, played_at
│   ├── flag_reasons[], severity, score
│   └── source: "spotify_recently_played"
├── weekly_reports/{week_start_iso} ← one row per week
│   ├── week_start, week_end, total_tracks, flagged_count
│   ├── flagged_tracks[] (denormalised, for offline dashboard)
│   ├── severity_breakdown {none, low, medium, high}
│   ├── top_genres[]
│   ├── severity (LOW / MEDIUM / HIGH / NONE)
│   ├── aggregate_score
│   └── generated_at, model_version
└── weekly_report_dismissals/{week_start_iso}  ← user closed the popup but
                                                 didn't delete the report
```

## Running locally

```bash
pip install -e .   # or: pip install -r sahara_listening/requirements.txt
python -m unittest tests.test_sahara_listening
```

## Deploying

```bash
export SAHARA_LISTENING_RESEARCH_MODE=true
modal secret create sahara-spotify-secret \
    SPOTIFY_CLIENT_ID=… SPOTIFY_CLIENT_SECRET=…
modal secret create sahara-firestore-secret \
    GOOGLE_APPLICATION_CREDENTIALS_JSON='…service-account-json…'
modal deploy sahara_listening/modal_deploy.py
```

The cron runs every Sunday at 18:00 UTC (Monday 00:00 PKT) and writes one
weekly report per opted-in user. The Android dashboard popup fires on the
*next time the user opens the dashboard* after a new report exists.

## Policy-clean alternative

If your Spotify Developer account is reviewed and the analysis is rejected,
the same Android UI works against the YouTube subscription path the top-level
README documents. Repoint `FlaggedTrack.source` to `youtube_subscription`
and write the same `activity_log_flags` / `weekly_reports` docs from the
YouTube ingestion cron instead.
