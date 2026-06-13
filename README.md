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
- **GitHub mirror:** exists only for tooling and external integrations.
- **Main docs:** use the [Codeberg Wiki](https://codeberg.org/solarmane/project-sahara/wiki).
- **Deployment docs:** [Deployment Guide](https://codeberg.org/solarmane/project-sahara/wiki/Deployment-Guide) and [Secrets And Local Setup](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup).
- **Patreon:** [https://patreon.com/project_sahara](https://patreon.com/project_sahara)

## Clone Or Fork

- Fork the repo on Codeberg if you are deploying your own copy.
- Clone your fork:
~~~sh
git clone https://codeberg.org/<your-user>/<your-fork>.git
cd <your-fork>
~~~
- Add upstream if needed:
~~~sh
git remote add upstream https://codeberg.org/solarmane/project-sahara.git
git fetch upstream
~~~
- Keep your branch current:
~~~sh
git switch main
git pull --ff-only upstream main
~~~

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
~~~sh
mkdir -p secrets
~~~
- Put real secret files only in `secrets/`:
  - `secrets/local.properties`
  - `secrets/google-services.json`
  - `secrets/client_secret.json`
  - `secrets/<firebase-admin-service-account>.json`
- Create symlinks from the repo root; do not copy secrets into place.
- Linux/macOS:
~~~sh
ln -sf secrets/local.properties local.properties
ln -sf ../secrets/google-services.json app/google-services.json
ln -sf ../secrets/client_secret.json app/client_secret.json
~~~
- Windows `cmd.exe` as Administrator or with Developer Mode:
~~~bat
mklink local.properties secrets\local.properties
mklink app\google-services.json ..\secrets\google-services.json
mklink app\client_secret.json ..\secrets\client_secret.json
~~~
- Windows PowerShell as Administrator or with Developer Mode:
~~~powershell
New-Item -ItemType SymbolicLink -Force -Path local.properties -Target (Resolve-Path secrets\local.properties)
New-Item -ItemType SymbolicLink -Force -Path app\google-services.json -Target (Resolve-Path secrets\google-services.json)
New-Item -ItemType SymbolicLink -Force -Path app\client_secret.json -Target (Resolve-Path secrets\client_secret.json)
~~~
- If symlinks fail on Windows:
  - enable Developer Mode or run the shell as Administrator
  - keep the repo on NTFS, not exFAT or a network drive
- Never commit `secrets/`, symlink targets, copied secrets, signing keys, API keys, PATs, or service-account JSON.

## Required Secrets

- **Firebase Android config:** [Firebase project settings](https://console.firebase.google.com/u/0/project/_/settings/general) -> Android app -> download `google-services.json`; see [Firebase Keys](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#firebase-keys).
- **Firebase Admin service account:** [Firebase service accounts](https://console.firebase.google.com/u/0/project/_/settings/serviceaccounts/adminsdk) -> Generate new private key; see [Firebase Keys](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#firebase-keys).
- **Firebase Auth:** [Firebase Authentication providers](https://console.firebase.google.com/u/0/project/_/authentication/providers) -> enable Email/Password and Google; see [Firebase Keys](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#firebase-keys).
- **Firebase databases:** [Firestore](https://console.firebase.google.com/u/0/project/_/firestore) and [Realtime Database](https://console.firebase.google.com/u/0/project/_/database) -> create databases and copy the RTDB URL; see [Firebase Keys](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#firebase-keys).
- **Firebase AI Logic / Gemini:** [Firebase AI Logic](https://firebase.google.com/docs/ai-logic/get-started?platform=android) -> enable Gemini for the Firebase project; see [Firebase Keys](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#firebase-keys).
- **Google OAuth client:** [Google Cloud Credentials](https://console.cloud.google.com/apis/credentials) -> create OAuth clients and download `client_secret.json`; see [Google OAuth And YouTube](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#google-oauth-and-youtube).
- **YouTube Data API:** [YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com) -> enable it for the project; see [Google OAuth And YouTube](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#google-oauth-and-youtube).
- **Modal secrets:** [Modal secrets](https://modal.com/docs/guide/secrets) -> create `firebase-admin`, `sahara-mail`, and `livekit`; see [Modal Secrets](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#modal-secrets).
- **LiveKit:** [LiveKit Cloud](https://cloud.livekit.io/) -> create project, API key, API secret, and server URL; see [Modal Secrets](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#modal-secrets).
- **Gmail SMTP:** [Google app passwords](https://support.google.com/accounts/answer/185833) -> create an app password for Modal `sahara-mail`; see [Modal Secrets](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#modal-secrets).
- **Render env vars:** [Render web service env vars](https://render.com/docs/configure-environment-variables) -> set OAuth helper environment variables; see [Render Environment Variables](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#render-environment-variables).
- **Spotify key:** [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) -> create app and copy client ID; see [Render Environment Variables](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#render-environment-variables).
- **Steam key:** [Steam Web API Key](https://steamcommunity.com/dev/apikey) -> create key; see [Render Environment Variables](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#render-environment-variables).
- **Bank/payment display keys:** set `sahara.bank.*` values in `secrets/local.properties` only if payment review is enabled; see [Local Properties Template](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup#local-properties-template).
- **Full steps and links:** [Secrets And Local Setup](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup).

## Python And Modal

- Create or activate a virtualenv:
~~~sh
python -m venv .venv
source .venv/bin/activate
~~~
- Fish:
~~~fish
source .venv/bin/activate.fish
~~~
- Install Modal:
~~~sh
pip install modal
~~~
- Login to Modal:
~~~sh
modal setup
~~~
- Deploy services from the repo/service paths documented in the wiki:
  - [Deployment Guide](https://codeberg.org/solarmane/project-sahara/wiki/Deployment-Guide)

## Android Run

- Open the repo in Android Studio.
- Confirm symlinks resolve:
  - `local.properties`
  - `app/google-services.json`
  - `app/client_secret.json`
- Sync Gradle.
- Build:
~~~sh
./gradlew :app:assembleDebug
~~~
- Run the `app` configuration on a device/emulator with Google Play services.

## Wiki Map

- [Current Architecture](https://codeberg.org/solarmane/project-sahara/wiki/Current-Architecture)
- [Deployment Guide](https://codeberg.org/solarmane/project-sahara/wiki/Deployment-Guide)
- [Secrets And Local Setup](https://codeberg.org/solarmane/project-sahara/wiki/Secrets-And-Local-Setup)
- [Services](https://codeberg.org/solarmane/project-sahara/wiki/Services)
- [Sahara AI Protocol](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-AI-Protocol)
- [Sahara Lens](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-Lens)
- [Sahara Voice](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-Voice)
- [Sahara Push Mailer And Biometric Auth](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-Push-Mailer-And-Biometric-Auth)
- [Sahara Risk](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-Risk)
- [Sahara Listening](https://codeberg.org/solarmane/project-sahara/wiki/Sahara-Listening)
- [Connections OAuth Helper](https://codeberg.org/solarmane/project-sahara/wiki/Connections-OAuth-Helper)
- [Firebase Support Files](https://codeberg.org/solarmane/project-sahara/wiki/Firebase-Support-Files)

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
