# Sahara Push, Mailer, And Biometric Auth

`modal_deploy.py` deploys one Modal app named `sahara-push`. It contains:

- FCM notification jobs for user notifications, admin review alerts, approval keys, and rejection emails.
- `sahara-mailer`, the transactional email endpoint used by Android signup verification and service emails.
- `sahara-biometric-enroll`, `sahara-biometric-login`, and `sahara-biometric-disable`, the per-device biometric login endpoints.

## Deploy

Run from the repository root with the project virtualenv:

```bash
.venv/bin/modal deploy services/sahara_push/modal_deploy.py
```

The deployment needs the existing Modal secrets:

- `firebase-admin` with `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_DB_URL`.
- `sahara-mail` with SMTP values for mail delivery.

## Android Config

After deploy, put the printed endpoint URLs in `local.properties`:

```properties
sahara.mailer.url=https://<workspace>--sahara-mailer.modal.run
sahara.biometric.enroll.url=https://<workspace>--sahara-biometric-enroll.modal.run
sahara.biometric.login.url=https://<workspace>--sahara-biometric-login.modal.run
sahara.biometric.disable.url=https://<workspace>--sahara-biometric-disable.modal.run
```

Biometric login does not store the user's password or Firebase refresh token.
Android stores only a random device id and secret in Keystore-backed encrypted
preferences. The Modal service stores a hash of that secret and mints a Firebase
custom token for the original UID after validation.
