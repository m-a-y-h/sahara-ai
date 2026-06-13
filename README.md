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
| AI chat endpoint | Firebase AI Logic SDK calling Gemini directly from Android |
| AI chat protocol | Prompting, safety parser, and SFT tooling in `services/sahara_ai/` |
| Base model credit | Fine-tuned from Qalb/Llama 3.1 |
| Facial-emotion screening (Lens) | Pretrained `dima806/facial_emotions_image_detection` ViT served on Modal (~91.9% on 25k FER test set) |
| Voice screening | `facebook/wav2vec2-xls-r-300m` fine-tuned on UrduSER, served on Modal (~91% precision on flagged distress, 86.6% accuracy on conf ≥ 0.65) |
| Maps/location | Google Play Services / Google Maps |
| Voice & video calls | LiveKit (in-app WebRTC); tokens minted by `services/sahara_livekit/` |
| Python service hosting | Modal for Lens, Voice, LiveKit tokens, risk jobs, listening jobs, push jobs, mailer, and biometric custom-token auth |
| OAuth connections helper | Node Express on Render — `services/connections_poc_server/` (Bluesky / Steam / Spotify; YouTube authorised on-device) |
| Push/auth support | Firebase Cloud Messaging plus mailer, admin notifier, key-delivery cron, and biometric custom-token endpoints in `services/sahara_push/` |

### Deployed endpoints

Python services run on the same Modal account (`its-asattar`). The Node OAuth helper runs on Render. URLs to drop into `local.properties`:

| Service | URL |
|---|---|
| Sahara Lens scan | `https://its-asattar--sahara-lens.modal.run/v1/lens/scan` |
| Sahara Voice analyze | `https://its-asattar--voice-endpoint.modal.run/v1/voice/analyze` |
| LiveKit token | `https://its-asattar--livekit-token.modal.run/token` |
| Sahara mailer | `https://its-asattar--sahara-mailer.modal.run` |
| Biometric enroll | `https://its-asattar--sahara-biometric-enroll.modal.run` |
| Biometric login | `https://its-asattar--sahara-biometric-login.modal.run` |
| Biometric disable | `https://its-asattar--sahara-biometric-disable.modal.run` |
| Connections OAuth helper | `https://sahara-connections.onrender.com` |

Firebase Auth, Firestore, and Realtime Database are the app backend services. The Android `data/` package is the client-side data layer that talks to Firebase and the Sahara AI endpoint. A separate FastAPI folder is only needed if we later deploy our own server outside Colab.

---

## Repository Layout

```text
.
├─ app/                                        Android app module (Kotlin + Jetpack Compose)
│  └─ src/main/java/pk/edu/ucp/saharaai/
│     ├─ data/                                 Firebase/Firestore/RealtimeDB repositories + models
│     ├─ viewmodels/                           Compose screen state holders
│     ├─ ui/screens/                           Compose screens
│     ├─ ui/components/                        Reusable Compose components
│     └─ service/                              Foreground services (app tracker, meditation music)
├─ services/                                   Python services (FastAPI, deployed on Modal)
│  ├─ sahara_ai/                               Chat protocol, safety parser, SFT tooling
│  ├─ sahara_lens/                             Facial-emotion screening model + API
│  ├─ sahara_voice/                            Voice-emotion screening model + API
│  ├─ sahara_listening/                        Listening-signal classifier (research)
│  ├─ sahara_livekit/                          LiveKit token server (in-app calls)
│  ├─ sahara_push/                             FCM push sender
│  ├─ connections_poc_server/                  Local Bluesky/Steam/Spotify OAuth helper
│  ├─ firebase/                                Firestore + Realtime DB security rules + seed data
│  └─ tests/                                   Python service tests
├─ secrets/                                    Local-only secrets (gitignored) — see "Secrets"
├─ docs/assets/                                README/documentation images
└─ gradle/, gradlew*, *.gradle.kts             Android/Gradle project configuration
```

The Android module is `:app`, so application source and Android resources live
under `app/`. Gradle wrapper/settings/build files must remain at repository
root in a standard Android Studio project; they are project configuration, not
application feature clutter.

---

## Android Setup

1. Open this folder in Android Studio.
2. Provide your secrets — see the **[Secrets](#secrets)** section below for the files and how to symlink them out of `secrets/`.
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

6. AI chat uses Firebase AI Logic's Gemini SDK directly. No `sahara.ai.chat.url`
   local property is required for the app.

7. Other optional local secrets:

```properties
sahara.admin.key=<admin-dashboard-key>
sahara.ngo.key=<ngo-key>
sahara.counselor.key=<counselor-key>
sahara.livekit.url=wss://<your>.livekit.cloud
sahara.livekit.token.url=<sahara_livekit Modal URL>/token
sahara.lens.scan.url=<sahara_lens Modal URL>/v1/lens/scan
sahara.voice.analyze.url=<sahara_voice Modal URL>/v1/voice/analyze
sahara.mailer.url=<sahara_push mailer Modal URL>
sahara.biometric.enroll.url=<sahara_push biometric enroll Modal URL>
sahara.biometric.login.url=<sahara_push biometric login Modal URL>
sahara.biometric.disable.url=<sahara_push biometric disable Modal URL>
sahara.bank.account.title=<receiving account title>
sahara.bank.iban=<iban or mobile-wallet number>
sahara.bank.name=<bank or wallet, e.g. HBL, JazzCash, Easypaisa>
sahara.bank.account.number=<account-number>
```

> The payment receiver is bank-agnostic: use `sahara.bank.*` for any bank or mobile wallet (HBL, JazzCash, Easypaisa, …), not just one bank.

8. Sync Gradle and run the Android module `:app`.

### Authentication And Biometric Login

Email/password and Google sign-in both use Firebase Authentication. Google
sign-in is allowed to use Firebase's one-account-per-email linking path, so an
existing password account can attach Google once and keep both sign-in methods
for the same UID. If Firebase refuses automatic linking with an account
collision, the login screen shows a one-time password fallback.

Fingerprint login is separate from the email/password provider. Enabling it in
Settings enrolls only this device: Android stores a random device id + secret in
Keystore-backed encrypted preferences, and `services/sahara_push/` stores only a
hash of that secret. After the biometric prompt succeeds, the app calls the
Modal biometric login endpoint, receives a Firebase custom token for the same
UID, and signs in with `signInWithCustomToken`. Clearing app data removes the
local device credential; disabling fingerprint login removes only that device's
biometric restore path and does not unlink Google or email/password.

## Secrets

The entire `secrets/` folder is gitignored, so **nothing here is ever committed.** The *real* files live in `secrets/`; the build expects three of them at fixed paths, so we symlink those out of `secrets/`:

| Real file in `secrets/`                | Symlinked to                | Needed by |
|----------------------------------------|-----------------------------|-----------|
| `local.properties`                     | `./local.properties`        | Gradle (SDK path + build config) |
| `google-services.json`                 | `app/google-services.json`  | google-services plugin (Firebase) |
| `client_secret.json`                   | `app/client_secret.json`    | Google OAuth |
| `<project>-firebase-adminsdk-*.json`   | *(not symlinked)*           | the `sahara_push` Modal secret only |

After you place your real files in `secrets/`, recreate the symlinks **from the repo root**. Run the block for your OS in one go (the `cd` keeps you in the right folder):

**Linux / macOS / FreeBSD / OpenBSD** — `sh`, `bash`, or `zsh` (macOS defaults to zsh, the BSDs to sh/ksh); `ln -s` is identical on all four:

```sh
cd /path/to/project-sahara && \
ln -sf secrets/local.properties local.properties && \
ln -sf ../secrets/google-services.json app/google-services.json && \
ln -sf ../secrets/client_secret.json app/client_secret.json
```

**Windows (NT)** — `cmd.exe`, **Run as Administrator** (or enable Settings → Privacy & security → For developers → Developer Mode, after which any shell can make links):

```bat
cd /d C:\path\to\project-sahara && ^
mklink local.properties secrets\local.properties && ^
mklink app\google-services.json ..\secrets\google-services.json && ^
mklink app\client_secret.json ..\secrets\client_secret.json
```

**Windows (NT)** — PowerShell, **Run as Administrator** (or Developer Mode). Uses absolute targets so it is PowerShell-version independent:

```powershell
cd C:\path\to\project-sahara
New-Item -ItemType SymbolicLink -Force -Path local.properties         -Target (Resolve-Path secrets\local.properties)
New-Item -ItemType SymbolicLink -Force -Path app\google-services.json -Target (Resolve-Path secrets\google-services.json)
New-Item -ItemType SymbolicLink -Force -Path app\client_secret.json   -Target (Resolve-Path secrets\client_secret.json)
```

**Symlinks giving you trouble?** (common on Windows without admin/Developer Mode, or on exFAT/network drives) — just **copy** the files into place instead. You lose the single-source-of-truth, but it always works:

```sh
# POSIX
cp secrets/local.properties local.properties
cp secrets/google-services.json app/google-services.json
cp secrets/client_secret.json app/client_secret.json
```
```bat
:: Windows cmd
copy secrets\local.properties local.properties
copy secrets\google-services.json app\google-services.json
copy secrets\client_secret.json app\client_secret.json
```

Never commit `secrets/`, the symlinks/copies it feeds, or any signing key.

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

The helper has three Bluesky operating modes, chosen at startup based on `BASE_URL` and the `BLUESKY_CLIENT_PRIVATE_JWK` env var. **Loopback** — `http://` `BASE_URL`, uses atproto's built-in loopback helper; convenient for local dev (`adb reverse`). **Confidential** — `https://` `BASE_URL` plus a private ES256 JWK; the server self-registers as a confidential OAuth client by serving its own `/client-metadata.json` and a JWKS at `/jwks.json` (public key only). The atproto OAuth directory fetches both URLs the first time a user starts a Bluesky auth flow on this server — no developer dashboard or directory registration step exists. **Disabled** — `https://` `BASE_URL` with no key configured; the three Bluesky routes return a clean 503. Steam + Spotify work on either protocol; YouTube authorisation happens entirely on-device via Google Identity Services and does not hit this server. Generate the ES256 keypair once with:

```bash
cd services/connections_poc_server
node scripts/generate-bluesky-key.mjs
```

The script prints the **private JWK** as a single line — paste it into the deploy host's env (Render → Environment → `BLUESKY_CLIENT_PRIVATE_JWK`) AND into `secrets/connections.env` for local dev. The public half is derived from it on startup and served at `/jwks.json`.

For local-only flows (without a public deploy), start the helper service and use loopback mode:

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

The admin dashboard accepts pending NGO/counselor registration forms, opens their submitted evidence, launches an email contact action, issues a key after manual review, and approves/rejects submitted payment screenshots. The NGO dashboard shows aggregate latest-assessment risk averages by voluntarily recorded user region and does not infer a region where none was supplied. A download button on the Regional Risk Averages section exports a CSV snapshot (header totals + per-region risk averages + counselor roster) through the Android Storage Access Framework so the NGO can save it to Downloads, Drive, or any picked location.

When admins open the dashboard their device is subscribed to the `admins` FCM topic. `services/sahara_push/` runs three minute-level jobs: `push_pending` (delivers in-app notifications written under `user_notifications/{uid}/{id}`), `notify_admins_of_pending` (walks registration / payment / bug-report queues and pings the `admins` topic when something new lands, then stamps the item so admins are not re-pinged about the same case), and `deliver_keys` (when an admin approves a registration, pushes the issued key to the applicant's device via the FCM token captured at submit time AND emails it via Gmail SMTP from the `sahara-mail` Modal secret).

Counselor visibility on the user-facing counselor list is driven by Firebase presence (`.info/connected` + `onDisconnect`), so a counselor with the dashboard open is auto-online to users without a manual switch. The dashboard's "Visible/Invisible" switch is now an override that hides the counselor from users without dropping the live connection. Offline counselors stay listed on the user side (sorted after online ones, with a grey status pip) so users can still queue a message.

### Firebase Security Requirement

The current key dialog is an app-level prototype gate. A value in `BuildConfig` can be extracted from an APK and is not sufficient authorization for payment screenshots or registration documents. The NGO aggregate screen also calculates summaries on the client from assessment records in this prototype; production code must read precomputed aggregates instead of granting NGOs raw assessment access. Before using real personal or financial evidence, protect Realtime Database and Firebase Storage reads/writes with authenticated admin/NGO roles enforced by Firebase Security Rules (normally custom claims set by a trusted admin backend), and retain only the minimum evidence needed for review.

Authentication, profile, emergency, dashboard, and other screen-specific backend work either lives in this repo or is provided by teammates' branches/services.

The Android side intentionally owns the app workflow: language selection, UI card routing, transcript writes, local unavailable-service fallback, and Firebase client access. The FastAPI layer receives only the chat text/language, recomputes safety-critical fields, clamps the response schema, and ignores any client attempt to override risk, counselor triggers, or user data access.

For Sahara Lens, merge [`services/firebase/firestore.sahara-lens.rules.snippet`](./services/firebase/firestore.sahara-lens.rules.snippet) into the live Firestore rules maintained by the backend team before release. It restricts check-in creation and reads to the signed-in owner, makes check-ins append-only, validates supported self-report values, and rejects any image upload or face-derived emotion field.

For sleep tracking, merge [`services/firebase/firestore.sahara-sleep.rules.snippet`](./services/firebase/firestore.sahara-sleep.rules.snippet) into the same deployed rules. It restricts sleep-log access to the signed-in owner and validates durations, source tags, and timezone metadata before weekly aggregation.

---

## Sahara Lens

Sahara Lens is the camera check-in feature accessed from the authenticated dashboard. It is an ML-based facial-emotion-recognition screening signal feeding the SAHARA risk pipeline. The serving pipeline lives in [`services/sahara_lens/`](./services/sahara_lens/).

Pipeline:

1. The front end captures a well-aligned, well-lit face image and rejects bad lighting, side angles, meme/troll faces, occlusions, and posture issues client-side, with minimal backend bypass guards.
2. The image is POSTed to `/v1/lens/scan`. The backend runs a server-side quality gate (face detection, blur/brightness checks), then the model, then the screening collapse onto the canonical four-class space (neutral / stress / sadness / fear).
3. The output is treated as a screening signal, never a diagnosis. A converged signal (high probability of fear/sadness plus matching self-report or check-in context) is what routes the user to Sahara harm-reduction support, counselor escalation, or local emergency resources.

Model:

- **Default backend:** `dima806/facial_emotions_image_detection` — an Apache-2.0 ViT-base fine-tuned for facial emotion classification across the full 7-class set (angry, disgust, fear, happy, neutral, sad, surprise), reported at ~91.9% accuracy on its 25 k test set. Switched on by `SAHARA_LENS_BACKEND=hf` in [`services/sahara_lens/modal_deploy.py`](./services/sahara_lens/modal_deploy.py); weights are baked into the Modal image so cold starts don't re-download.
- **Optional in-house backend** (`services/sahara_lens/train.py`, `model.py`): hybrid ResNet-50 + ViT trainable with `--select-metric val_acc` for datasets that lack the screening-distress classes (e.g. IIITM). Default model-selection metric remains the production ship-gate.
- The Android side and the `/scan` response shape are identical whether the HF or in-house backend is loaded, so the model can be swapped without rebuilding the app.

---

## Sahara AI Protocol

The Android app now uses Firebase AI Logic's Gemini SDK directly for live AI chat, so there is no Sahara AI chat Modal proxy to deploy. The `services/sahara_ai/` package remains as the safety/protocol reference and offline test layer: it handles Pakistani slang, misspellings, JSON validation, emergency escalation, and harm-reduction response constraints for service-style deployments or model evaluation.

Run protocol tests:

```bash
cd services
python -m unittest tests/test_sahara_ai_protocol.py
```

Generate seed SFT data (the curated Roman-Urdu distress scenarios used to brief the chat model):

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

Project Advisors:

- Prof. U. Aamer
- Hafiz U. Ishtiaq
