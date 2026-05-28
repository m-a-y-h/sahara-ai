# Sahara Risk — per-user weekly risk-score adjustment

A small, unsupervised, per-user model that turns weekly activity logs into
a continuously-updated risk score over a 26-week (6-month) monitoring
window. There is no global ML training step — each user is their own
baseline (running mean + variance), and the score is updated with a
calibrated EWMA every Monday.

## Lifecycle

```
DAST-10/20 completion
  │
  ▼ (Android bootstraps Firestore cycle docs or calls register_user_for_monitoring)
Initial profile written + monitoring window opened
  │
  ▼ wait 1 week
Monitoring start_notice popup shown on dashboard (one-shot)
  │
  ├── week 1 ┐
  │   …      │  EWMA updates every Monday for 26 weeks
  │   week 26│  each update reads the prior week's activity-log subcollections
  ▼          ▼
Monitoring ends → cumulative report written, reassessment required,
                  per-cycle activity reset
```

## Package layout

```
services/sahara_risk/
├── __init__.py
├── config.py          DAST→risk mapping, EWMA alpha schedule, feature weights
├── features.py        WeeklyActivitySnapshot + WeeklyFeatureVector dataclasses
├── monitoring.py      MonitoringPeriod + start/end helpers
├── model.py           UserRiskProfile + update_weekly (the actual math)
├── aggregator.py      Firestore reader for the 11 activity subcollections
├── firestore_writer.py
├── modal_deploy.py    weekly cron + register_user_for_monitoring entrypoint
└── README.md
```

## What gets read per week

All under `users/{uid}/`:

| Subcollection                | Aggregated into                | Cap          |
|------------------------------|--------------------------------|--------------|
| `sahara_lens_checkins`       | FaceEmotionSummary             | 7/week       |
| `sahara_voice_checkins`      | VoiceEmotionSummary            | 7/week       |
| `chat_messages` (by uid)     | ChatSentimentSummary           | unlimited    |
| `journal_entries`            | JournalSummary                 | 21/week      |
| `sleep_logs`                 | SleepSummary (with fallback)   | 7/week       |
| `weekly_reports/{week}`      | ListeningMusicSummary          | 1/week       |
| `social_post_flags`          | SocialPostsSummary             | unlimited    |
| `steam_play_flags`           | SteamGamesSummary              | unlimited    |
| `youtube_sub_flags`          | YouTubeSubsSummary             | unlimited    |
| `screen_time_log`            | ScreenTimeSummary              | per-day      |
| `recovery_points_log`        | RecoveryCreditSummary          | unlimited    |

Any source that is missing for the week is treated **neutrally** — no
contribution, no penalty. The weights of the remaining sources are
re-normalised so the weekly observation stays in `[0, 1]`.

## Sleep fallback ladder

`SleepSummary.source_per_day` is a typed `SleepSource` per slot, audited
into Firestore so a reviewer can see exactly where each night's hours came
from:

1. **Self-reported** — the user logged it in the app
2. **Health Connect** — duration recorded by an authorized compatible sleep
   app or wearable and imported by the user
3. **Actigraphy estimate** — an opt-in Android foreground service's
   phone-motion estimate, stored with its confidence and source reason
4. **Six-month average** — the user's own running mean of the prior 26
   weeks
5. **Default** — first-week fallback, hardcoded at `DEFAULT_SLEEP_HOURS`
   (6.0 h/night), used only when neither self-reported nor history is
   available

The Android service collects local five-minute motion summaries only while its
persistent notification is active. A still phone without nearby movement or
enough sampling is inconclusive and falls through to prior average/default.
The proposed lifestyle dataset does not contain labeled actigraphy samples,
so these values are explicitly estimates rather than a trained detection
claim.

## Why EWMA + per-user z-score

Two principles:

* **Per-user baseline**: a heavy doom-metal listener should not be flagged
  forever just for staying in their listening niche. The model tracks each
  feature's running mean and std *per user* via Welford's algorithm, and
  the observation each week is the user's *z-score against themselves*.
  Two standard deviations above their own mean reads as 1.0; their own
  mean reads as 0.5; two below reads as 0.0.
* **Slow during calibration, faster later**: weeks 1–4 use `α=0.90` so a
  noisy first month can't whiplash the score; weeks 5–12 use `α=0.75`;
  weeks 13+ use `α=0.60`. Per-week deltas are also hard-clamped to
  `[−0.10, +0.15]`.

## Recovery counter-signal

The gamified-recovery points + streak push the observation **down** before
the EWMA. The weight is `0.20` of the points-and-streak signal, so a
strong recovery week can offset roughly 0.2 of the risk observation —
enough to hold the score steady through a noisy week of activity.

## 6-month report and reset

When the monitoring window ends, the cron writes
`users/{uid}/cumulative_reports/{cycle_id}` and marks
`users/{uid}/lifecycle/current.assessment_required = true`. The Android
client shows the report, makes the assessment available again, and locks
the risk calculators plus chat until the next assessment is completed.

The reset clears only cycle-specific monitoring inputs: risk profile/history,
active monitoring docs, lens/voice check-ins, listening reports, journal,
sleep, screen-time, and other risk-source flags. Chat history, cumulative
reports, and recovery/progress state are preserved.

## Tests

The model is pure-Python. Date/source reduction for sleep is unit-tested;
Firestore reads and writes still require staging verification before turning
on the Modal schedule.

```bash
cd services && python -m unittest tests.test_sahara_risk
```
