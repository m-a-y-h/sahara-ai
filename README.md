<div align="center">
    <img src="./docs/assets/logo.webp" alt="SAHARA AI Logo" width="192" />
    <h1>SAHARA AI</h1>
    <p><b>S</b>ubstance <b>A</b>buse — <b>H</b>arm <b>A</b>ssessment, <b>R</b>esponse & <b>A</b>ssistance</p>
</div>

---

> [!CAUTION]
> SAHARA AI is an FYP/research prototype in active development. It is not a medical device, diagnostic system, or emergency-service replacement.

---

- **What this is:** Android harm-reduction support prototype for non-prescribed drug use, built with Kotlin, Jetpack Compose, Firebase, Gemini via Firebase AI Logic, Modal services, Render, and LiveKit.
- **Canonical repo:** [Codeberg](https://codeberg.org/solarmane/project-sahara).
- **GitHub mirror:** exists only for tooling and the wiki.
- **Main docs:** use the [GitHub Wiki](https://github.com/m-a-y-h/sahara-ai/wiki) because Codeberg wiki is not enabled for this mirror workflow.
- **Deployment docs:** [Deployment Guide](https://github.com/m-a-y-h/sahara-ai/wiki/Deployment-Guide) and [Secrets And Local Setup](https://github.com/m-a-y-h/sahara-ai/wiki/Secrets-And-Local-Setup).
- **Patreon:** [https://patreon.com/project_sahara](https://patreon.com/project_sahara)

## Clone Or Fork

- Fork the repo on Codeberg if you are deploying your own copy.
- Clone your fork:
  - `git clone https://codeberg.org/<your-user>/<your-fork>.git`
  - `cd <your-fork>`
- Add upstream if needed:
  - `git remote add upstream https://codeberg.org/solarmane/project-sahara.git`
  - `git fetch upstream`
- Keep your branch current:
  - `git switch main`
  - `git pull --ff-only upstream main`

## Apps To Install

- **Android Studio:** open and run the `:app` Android module.
- **Git:** clone, branch, commit, and sync the repo.
- **JDK:** use Android Studio's bundled JBR unless your OS setup already provides a compatible JDK.
- **Python:** use Python 3.11+ for Modal services.
- **Node.js:** use Node 22+ for `services/connections_poc_server`.
- **Modal CLI:** deploy Python services.
- **Render account:** deploy the Node OAuth helper.
- **Firebase project:** Authentication, Firestore, Realtime Database, FCM, Firebase AI Logic.
- **LiveKit Cloud project:** voice/video call server URL and token credentials.
- **Google Cloud Console:** OAuth clients and YouTube Data API.
- **Gmail or SMTP provider:** transactional email for the Modal mailer.

## Local Setup

- Create the local secret folder:
  - `mkdir -p secrets`
- Put real secret files only in `secrets/`:
  - `secrets/local.properties`
  - `secrets/google-services.json`
  - `secrets/client_secret.json`
  - `secrets/<firebase-admin-service-account>.json`
- Create symlinks from the repo root; do not copy secrets into place.
- Linux/macOS:
  - `ln -sf secrets/local.properties local.properties`
  - `ln -sf ../secrets/google-services.json app/google-services.json`
  - `ln -sf ../secrets/client_secret.json app/client_secret.json`
- Windows `cmd.exe` as Administrator or with Developer Mode:
  - `mklink local.properties secrets\local.properties`
  - `mklink app\google-services.json ..\secrets\google-services.json`
  - `mklink app\client_secret.json ..\secrets\client_secret.json`
- Windows PowerShell as Administrator or with Developer Mode:
  - `New-Item -ItemType SymbolicLink -Force -Path local.properties -Target (Resolve-Path secrets\local.properties)`
  - `New-Item -ItemType SymbolicLink -Force -Path app\google-services.json -Target (Resolve-Path secrets\google-services.json)`
  - `New-Item -ItemType SymbolicLink -Force -Path app\client_secret.json -Target (Resolve-Path secrets\client_secret.json)`
- If symlinks fail on Windows:
  - enable Developer Mode or run the shell as Administrator
  - keep the repo on NTFS, not exFAT or a network drive
- Never commit `secrets/`, symlink targets, copied secrets, signing keys, API keys, PATs, or service-account JSON.

## Required Secrets

- **Firebase Android config:** create the Android app in Firebase Console, download `google-services.json`, and place it at `secrets/google-services.json`.
- **Firebase Admin service account:** Firebase Console -> Project settings -> Service accounts -> Generate new private key; use the JSON only for Modal `firebase-admin`.
- **Firebase Auth:** Firebase Console -> Authentication -> Sign-in method; enable Email/Password and Google.
- **Firebase database keys:** Firebase Console -> Firestore Database and Realtime Database; copy the RTDB URL into Modal/Firebase config where needed.
- **Firebase AI Logic / Gemini:** Firebase Console -> AI Logic; enable the Gemini backend used by the Android app.
- **Google OAuth client:** Google Cloud Console -> APIs & Services -> Credentials; download OAuth JSON as `secrets/client_secret.json` when a local flow needs it.
- **YouTube Data API:** Google Cloud Console -> API Library -> YouTube Data API v3; enable it for the same signing key/package.
- **Modal secrets:** Modal dashboard/CLI; create `firebase-admin`, `sahara-mail`, and `livekit`.
- **LiveKit:** LiveKit Cloud -> project settings; copy server URL, API key, and API secret.
- **Gmail SMTP:** Google Account -> App passwords; use the app password for Modal `sahara-mail`, or use another SMTP provider.
- **Render env vars:** Render service settings; set `BASE_URL`, `SPOTIFY_CLIENT_ID`, `STEAM_WEB_API_KEY`, and `BLUESKY_CLIENT_PRIVATE_JWK` as needed.
- **Bank/payment display keys:** set `sahara.bank.*` values in `secrets/local.properties` only if payment review is enabled.
- **Full steps and links:** [Secrets And Local Setup](https://github.com/m-a-y-h/sahara-ai/wiki/Secrets-And-Local-Setup).

## Python And Modal

- Create or activate a virtualenv:
  - `python -m venv .venv`
  - `source .venv/bin/activate`
  - or Fish: `source .venv/bin/activate.fish`
- Install Modal:
  - `pip install modal`
- Login to Modal:
  - `modal setup`
- Deploy services from the repo/service paths documented in the wiki:
  - [Deployment Guide](https://github.com/m-a-y-h/sahara-ai/wiki/Deployment-Guide)

## Android Run

- Open the repo in Android Studio.
- Confirm symlinks resolve:
  - `local.properties`
  - `app/google-services.json`
  - `app/client_secret.json`
- Sync Gradle.
- Build:
  - `./gradlew :app:assembleDebug`
- Run the `app` configuration on a device/emulator with Google Play services.

## Wiki Map

- [Current Architecture](https://github.com/m-a-y-h/sahara-ai/wiki/Current-Architecture)
- [Deployment Guide](https://github.com/m-a-y-h/sahara-ai/wiki/Deployment-Guide)
- [Secrets And Local Setup](https://github.com/m-a-y-h/sahara-ai/wiki/Secrets-And-Local-Setup)
- [Services](https://github.com/m-a-y-h/sahara-ai/wiki/Services)
- [Sahara AI Protocol](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-AI-Protocol)
- [Sahara Lens](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-Lens)
- [Sahara Voice](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-Voice)
- [Sahara Push Mailer And Biometric Auth](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-Push-Mailer-And-Biometric-Auth)
- [Sahara Risk](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-Risk)
- [Sahara Listening](https://github.com/m-a-y-h/sahara-ai/wiki/Sahara-Listening)
- [Connections OAuth Helper](https://github.com/m-a-y-h/sahara-ai/wiki/Connections-OAuth-Helper)
- [Firebase Support Files](https://github.com/m-a-y-h/sahara-ai/wiki/Firebase-Support-Files)

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](https://codeberg.org/solarmane/project-sahara/src/branch/main/LICENSE).

## Team

University of Central Punjab — Faculty of Information Technology

- F. Yaseen
- A. R. Tariq
- M. A. Y. Haider

Project Advisors:

- Prof. U. Aamer
- Hafiz U. Ishtiaq
