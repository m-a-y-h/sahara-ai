# Services

This directory contains development services and ML prototypes consumed by, or
supporting, the Android `app/` module. They are deliberately separate from the
Android application source because they run as Node.js or Python/Modal
processes.

Run Python package and Modal commands from this directory so package imports
remain stable:

```bash
cd services
python -m unittest tests/test_sahara_ai_protocol.py
```

Contents:

- `connections_poc_server/`: local Bluesky, Steam, and Spotify connection helper.
- `sahara_ai/`: chat safety/router service.
- `sahara_lens/`: image screening model service.
- `sahara_voice/`: voice screening model service.
- `sahara_listening/`: research-only Spotify listening prototype.
- `sahara_push/`: Modal FCM jobs, transactional mailer, and biometric custom-token auth endpoints.
- `tests/`: Python tests for the services.

Deploy the push/mailer/biometric service from the repository root with the
project virtualenv:

```bash
.venv/bin/modal deploy services/sahara_push/modal_deploy.py
```

Modal prints four Android-facing URLs from that service: `sahara-mailer`,
`sahara-biometric-enroll`, `sahara-biometric-login`, and
`sahara-biometric-disable`. Put them in `local.properties` as
`sahara.mailer.url`, `sahara.biometric.enroll.url`,
`sahara.biometric.login.url`, and `sahara.biometric.disable.url`.
