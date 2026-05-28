<div align="center">
    <img src="./docs/assets/logo.webp" alt="SAHARA AI Logo" width="192" />
    <h1>SAHARA AI</h1>
    <p><b>S</b>ubstance <b>A</b>buse — <b>H</b>arm <b>A</b>ssessment, <b>R</b>esponse & <b>A</b>ssistance</p>
</div>

---

> [!CAUTION]
> SAHARA AI is an FYP/research prototype in active development. It is not a medical device, diagnostic system, or emergency-service replacement.

---

## Overview

SAHARA AI is a privacy-first Android support app for Pakistani youth dealing with non-prescribed drug use, misuse, relapse risk, overdose scares, or addiction-related distress.

The scope is intentionally limited to non-prescribed drug use. This includes street drugs, party drugs, inhalants, cannabis/charas, opioids/heroin/chitta, ice/methamphetamine, cocaine, MDMA, and controlled pharmacy medicines when they are taken without medical authorization or outside medical supervision. It is not designed for routine medication adherence or general pharmacy advice.

The app combines culturally aware chat support, Sahara AI harm-reduction responses, counselor/emergency escalation, private self-reported check-ins, anonymous recovery tooling, and Firebase-backed user data.

---

## Current Architecture

| Layer | Technology |
|---|---|
| Android app | Kotlin, Jetpack Compose |
| Auth | Firebase Authentication |
| Database | Cloud Firestore + Firebase Realtime Database |
| Android data layer | `data/model`, `data/remote`, and `data/repository` |
| Android state layer | `viewmodels` |
| AI chat endpoint | Sahara AI served from Colab/FastAPI |
| AI chat protocol | Prompting, safety parser, and SFT tooling in `services/sahara_ai/` |
| Base model credit | Fine-tuned from Qalb/Llama 3.1 |
| Maps/location | Google Play Services / Google Maps |
| Voice & video calls | LiveKit (in-app WebRTC); tokens minted by `services/sahara_livekit/` |
| Python model hosting | Modal (scale-to-zero) — `services/sahara_*/modal_deploy.py` |
| Push notifications | Firebase Cloud Messaging; sender in `services/sahara_push/` |

Firebase Auth, Firestore, and Realtime Database are the app backend services. The Android `data/` package is the client-side data layer that talks to Firebase and the Sahara AI endpoint. A separate FastAPI folder is only needed if we later deploy our own server outside Colab.

---

## Repository Layout

```text
app/                                Android Studio application module
app/src/main/java/pk/edu/ucp/saharaai/data/
                                    Firebase/Auth/Firestore/RealtimeDB repositories and models
app/src/main/java/pk/edu/ucp/saharaai/viewmodels/
                                    UI state holders for Compose screens
app/src/main/java/pk/edu/ucp/saharaai/ui/screens/
                                    Compose screens
app/src/main/java/pk/edu/ucp/saharaai/ui/components/
                                    Reusable Compose components
app/src/main/java/pk/edu/ucp/saharaai/service/
                                    Foreground services (app tracker, meditation music)
services/                           Non-Android APIs and local integration helpers
services/sahara_ai/                 Sahara AI chat protocol, safety parser, and SFT tooling
services/sahara_lens/               Facial screening API/model prototype
services/sahara_voice/              Voice screening API/model prototype
services/sahara_listening/          Research-only listening classifier prototype
services/connections_poc_server/    Local Bluesky/Steam/Spotify connection helper
services/tests/                     Python service tests
services/sahara_livekit/            LiveKit token server (Modal) for in-app calls
services/sahara_push/               FCM push sender (Modal) for notifications
services/firebase/                  Firestore + Realtime DB security rules and seed data
secrets/                            Local-only secrets (gitignored): service-account key + symlinks
docs/assets/                        README/documentation images
gradle/, gradlew*, *.gradle.kts     Required Android/Gradle project configuration
```

The Android module is `:app`, so application source and Android resources live
under `app/`. Gradle wrapper/settings/build files must remain at repository
root in a standard Android Studio project; they are project configuration, not
application feature clutter.

---

## Android Setup

1. Open this folder in Android Studio.
2. Keep local Android secrets in `local.properties` at the repository root.
3. Add the Firebase Android config file at:

```text
app/google-services.json
```

4. In the same Firebase project, open **Authentication > Sign-in method** and enable **Email/Password** and **Google**. The current local Android config targets project `sahara-ai-2ac5a`; replace `google-services.json` first if the team intends to use a different existing Firebase project.

5. Add the SHA-1 and SHA-256 certificate fingerprints for each APK signing key under **Project settings > Your apps > Android app** in Firebase, then download the updated `google-services.json`. For the local debug key currently used on this machine:

```text
SHA-1:   D9:DA:EB:36:90:5F:67:5D:EB:1A:7D:C8:12:A6:D2:81:6A:9F:E9:E3
SHA-256: D4:54:A5:70:C1:FE:EF:75:03:CE:61:6F:FF:0D:82:DD:74:E4:BD:E3:DE:48:12:7D:D5:08:6C:8A:25:4C:F4:B3
```

The checked-in configuration currently contains a different Android certificate fingerprint, so a locally signed debug APK cannot complete Google sign-in until Firebase is updated.

5. Point the chat screen at your live Sahara AI/FastAPI endpoint by adding this to `local.properties`:

```properties
sahara.ai.chat.url=https://your-colab-or-api-url/v1/chat
```

6. Other optional local secrets:

```properties
sahara.bypass.code=000000
sahara.admin.key=<admin-dashboard-key>
sahara.ngo.key=<ngo-key>
sahara.counselor.key=<counselor-key>
emailjs.service.id=<service-id>
emailjs.template.id=<template-id>
emailjs.public.key=<public-key>
sahara.livekit.url=wss://<your>.livekit.cloud
sahara.livekit.token.url=<sahara_livekit Modal URL>/token
sahara.lens.scan.url=<sahara_lens Modal URL>/v1/lens/scan
sahara.voice.analyze.url=<sahara_voice Modal URL>/v1/voice/analyze
sahara.hbl.account.title=<receiving account title>
sahara.hbl.iban=<iban>
sahara.hbl.bank=<bank>
sahara.hbl.account.number=<account-number>
```

7. Sync Gradle and run the Android module `:app`.

All secrets live in the gitignored `secrets/` folder — the real `local.properties`, `google-services.json`, and `client_secret.json` are kept there and symlinked into the build paths, so nothing sensitive sits loose in the tree. Never commit `secrets/` or signing keys.

## Social Connection Prototype

The Connections screen now has local proof-of-concept flows for Bluesky, Steam,
Spotify and YouTube. Bluesky displays Sahara's public-post analysis disclosure before
identity-only OAuth, stores the verified DID and handle with the consent
version, and does not request permission to write content or read private data.
Steam displays a visible-game-activity disclosure before browser OpenID and
stores the verified SteamID with its consent record. Steam activity retrieval
requires a server-held Web API key and is limited by the user's Steam privacy
settings. Spotify links the account identity through PKCE with minimal profile
access; Spotify listening content is not analyzed because its policy prohibits
health-related inferences from Spotify content or the service. YouTube verifies
the user's owned channel through Google OAuth and, after an explicit
subscription-analysis disclosure, stores up to 1,000 subscribed channel
IDs/titles for the later non-prescribed drug-use risk indicator. It does not
retrieve videos, comments or viewing history in this flow. YouTube
authorization runs in Android through Google Play services; its saved
authorized data expires after 30 days and is removed on the app's next
synchronization after expiry. Using Unlink revokes Sahara's YouTube access.

For local development it uses AT Protocol's supported loopback OAuth mode, so
the app does not have to be deployed. Start the helper service:

```bash
cd services/connections_poc_server
npm install
npm start
```

Forward the helper port into an attached emulator or Android device:

```bash
adb reverse tcp:8787 tcp:8787
```

Then run the Android app, log in, and connect a supported provider from the
Connections screen. Further setup and the read-only preview endpoints are
documented in [`services/connections_poc_server/README.md`](./services/connections_poc_server/README.md).

This path is for local proof-of-concept work only. Before production, the OAuth
completion and storage of verified provider identities must move to a trusted
backend so a forged application callback cannot be persisted as a connection.

### Low-memory machines

The committed `gradle.properties` ships **healthy defaults** (4 GB heap, parallel builds, all
cores) so a fresh clone builds fast on any machine. If you are on a low-RAM laptop, put your
throttle in your **user-global** `~/.gradle/gradle.properties` (e.g. `-Xmx2g`,
`org.gradle.workers.max=2`, `org.gradle.parallel=false`). That file overrides the project
settings for your machine only and is never committed.

---

## Firebase And Chat Data Layer

The Android app uses Firebase directly where this repo owns the client-side data layer. Current in-repo data code covers:

- `ChatRepository` — AI chat sessions stored in Firestore (`ai_<uid>` session IDs) and counselor sessions; includes a rule-based local AI reply fallback.
- `ChatViewModel` — owns `ChatScreen` state for both AI chat (Firestore) and counselor chat (Realtime DB), with typing state, message read receipts, and counselor notification dispatch.
- `LoginViewModel`, `RegisterViewModel`, `ForgotPasswordViewModel` — Firebase Authentication sessions and Firebase password-reset emails so authenticated Firestore ownership checks are meaningful.
- `RealtimeDBService` — Firebase Realtime Database access for user↔counselor chat, sleep tracking, assessments, and notifications.
- `FirestoreService` — Firestore access for AI chat transcripts and shared collections.
- `SleepTrackerViewModel` — weekly sleep tracker state holder; syncs source-tagged
  daily records for analysis and imports authorized Health Connect sleep durations.

Current Firestore collections:

- `messages` — AI chat transcripts (`sessionId` = `ai_<uid>`)
- `counselors`, `emergency_alerts`, `reports`, `community_posts`
- `users/{uid}/sahara_lens_checkins` (reserved for the future Sahara Lens rebuild)
- `users/{uid}/sleep_logs/{yyyy-MM-dd}` — user-owned sleep duration, source, and
  phone timezone used by `services/sahara_risk`.

Current Realtime Database review paths:

- `registration_requests/{requestId}` — NGO/counselor applications with uploaded-document URLs and manual review status.
- `ngo_keys/{key}` — approved NGO access key metadata, including the region used to scope its statistics.
- `payment_requests/{requestId}` — user-submitted peer-to-peer payment screenshots with `PENDING_REVIEW`, `ASSIGNED`, or `REJECTED` status.
- `users/{uid}/region` — optional user-provided region used for aggregate NGO risk reporting.
- `sleep/{uid}/{yyyy-MM-dd}` — UI-facing weekly sleep record mirror.

### Sleep Tracking And Weekly Analysis

The Sleep Tracker accepts manual bedtime/wake-time records for any elapsed
day in the current week and can import recorded sleep duration from Android
Health Connect when the user grants `READ_SLEEP`. Each record is tagged
`self_reported` or `health_connect` and stores the phone timezone. The screen
shows coverage, 7-to-9-hour range counts, and source provenance; this is a
wellness summary rather than a diagnosis.

An optional **Automatic Sleep Estimate** control starts a foreground
`SleepActigraphyService` only after the user grants activity-recognition and
notification permission. While active, the persistent notification says that
Sahara is estimating sleep from phone motion and includes a Stop action. The
service stores five-minute accelerometer summaries locally, not raw samples in
Firebase. For a missing night, a bounded quiet block with adequate sampling
and nearby phone movement becomes a source-tagged `actigraphy` estimate. A
stationary/unused phone is rejected as inconclusive and uses the prior
six-month measured average (`six_month_average`), or `default` at 6 hours when
there is no prior measured history. Users can replace any automatic entry
with a manual log or Health Connect import.

A wearable or compatible sleep app can populate Health Connect; manual
logging remains available when automatic tracking or Health Connect is absent
or permission is withheld. Health Connect support requires Android 8.0/API 26
or later, so the Android module minimum SDK is 26.

The referenced Sleep Health and Lifestyle dataset is a cross-sectional
lifestyle/outcome dataset; it is not labeled accelerometer data and cannot
validate automatic sleep-interval detection. Automatic phone-motion values
are therefore labeled as estimates with provenance and confidence rather than
presented as a trained actigraphy model.

## Manual Counselor Payment Workflow

This prototype does not use Google Pay, RevenueCat, or automated payment verification. A user transfers an agreed amount outside the app, selects a counselor, uploads a screenshot in the counselor list, and waits for manual admin review. Approval creates the existing user-to-counselor chat assignment and sends a notification to the user.

The NGO/Admin key entry in welcome settings now distinguishes:

- `sahara.admin.key` — opens the admin review dashboard.
- `sahara.ngo.key` or an approved `ngo_keys/{key}` value — opens the NGO dashboard.
- An issued `counselor_keys/{key}` value — opens counselor setup/dashboard.

The admin dashboard accepts pending NGO/counselor registration forms, opens their submitted evidence, launches an email contact action, issues a key after manual review, and approves/rejects submitted payment screenshots. The NGO dashboard shows aggregate latest-assessment risk averages by voluntarily recorded user region and does not infer a region where none was supplied.

### Firebase Security Requirement

The current key dialog is an app-level prototype gate. A value in `BuildConfig` can be extracted from an APK and is not sufficient authorization for payment screenshots or registration documents. The NGO aggregate screen also calculates summaries on the client from assessment records in this prototype; production code must read precomputed aggregates instead of granting NGOs raw assessment access. Before using real personal or financial evidence, protect Realtime Database and Firebase Storage reads/writes with authenticated admin/NGO roles enforced by Firebase Security Rules (normally custom claims set by a trusted admin backend), and retain only the minimum evidence needed for review.

Authentication, profile, emergency, dashboard, and other screen-specific backend work either lives in this repo or is provided by teammates' branches/services.

The Android side intentionally owns the app workflow: language selection, UI card routing, transcript writes, local unavailable-service fallback, and Firebase client access. The FastAPI layer receives only the chat text/language, recomputes safety-critical fields, clamps the response schema, and ignores any client attempt to override risk, counselor triggers, or user data access.

For Sahara Lens, merge [`services/firebase/firestore.sahara-lens.rules.snippet`](./services/firebase/firestore.sahara-lens.rules.snippet) into the live Firestore rules maintained by the backend team before release. It restricts check-in creation and reads to the signed-in owner, makes check-ins append-only, validates supported self-report values, and rejects any image upload or face-derived emotion field.

For sleep tracking, merge [`services/firebase/firestore.sahara-sleep.rules.snippet`](./services/firebase/firestore.sahara-sleep.rules.snippet) into the same deployed rules. It restricts sleep-log access to the signed-in owner and validates durations, source tags, and timezone metadata before weekly aggregation.

---

## Sahara Lens

Sahara Lens is the camera check-in feature accessed from the authenticated dashboard. It is an ML-based facial-emotion-recognition screening model targeted at the South Asian (KPK, Punjab, Sindh, Balochistan) population. The training and serving pipeline lives in [`services/sahara_lens/`](./services/sahara_lens/).

Planned behaviour:

1. The front end captures a well-aligned, well-lit face image and rejects bad lighting, side angles, meme/troll faces, occlusions, and posture issues client-side, with minimal backend bypass guards.
2. Once a clean capture is accepted, the image is sent to the backend, which runs the FER model to detect the negative-emotion signal cluster (stress, sadness, fear) associated with non-prescribed drug use.
3. The output is treated as a screening signal, never a diagnosis. A converged signal (e.g. high probability of fear/sadness plus matching self-report or check-in context) is what routes the user to Sahara harm-reduction support, counselor escalation, or local emergency resources.

Model strategy:

- Architecture: hybrid ResNet-50 + Vision Transformer (ViT), or a Swin Transformer backbone, pre-trained on ImageNet and fine-tuned for the three negative-emotion classes.
- Primary dataset: **Mendeley "Indian face images with various expressions"** (`zfcd4bny82`); the loader maps its labels onto the canonical screening space (stress, sadness, fear). Voice screening uses **UrduSER** (`jcpfjnk5c2`). Earlier exploration referenced InFER++.
- Training discipline: target ≥92% per-class F1 on the three negative emotions (not just aggregate top-1 accuracy), with class rebalancing, intersectional bias audits, and a multimodal fallback path so a single misread frame cannot escalate alone.
- Ethics: outputs are screening signals, not diagnoses; human-in-the-loop counselor review for any escalation; explicit consent and on-device privacy where possible.

The previous minimal Sahara Lens implementation (self-reported check-in only, no FER) has been removed from this branch and is not part of the current Android codebase.

---

## Sahara AI Protocol

The `services/sahara_ai/` package contains the Sahara AI safety/router layer used by the `/v1/chat` Colab or API deployment. It handles Pakistani slang, misspellings, JSON validation, emergency escalation, and harm-reduction response constraints.

Run tests:

```bash
cd services
python -m unittest tests/test_sahara_ai_protocol.py
```

Generate seed SFT data:

```bash
cd services
python -m sahara_ai.generate_sahara_ai_sft_dataset --output sahara_ai/sahara_ai_sft_seed.jsonl
```

---

## Safety Scope

SAHARA AI prioritizes helpful harm-reduction support:

- Recognize Pakistani youth slang and misspellings for non-prescribed substances.
- Give immediate overdose-risk guidance when the user mentions breathing trouble, unconsciousness, blue lips, seizures, chest pain, overheating, dangerous mixing, or self-harm.
- Trigger counselor/emergency escalation in high-risk cases.
- Refuse instructions for getting high, dosing misuse, buying drugs, hiding use, evading parents/police, or passing drug tests.

SAHARA AI should not be used as a replacement for doctors, hospitals, rescue services, or licensed addiction treatment.

---

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](./LICENSE).

---

## Team

University of Central Punjab — Faculty of Information Technology

- F. Yaseen
- A. R. Tariq
- M. A. Y. Haider

Project Advisor: Prof. U. Aamer
