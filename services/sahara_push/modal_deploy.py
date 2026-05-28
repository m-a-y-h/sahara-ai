"""FCM push sender — delivers SAHARA in-app notifications as real device pushes.

The app writes notifications to RTDB ``user_notifications/{uid}/{id}`` and saves FCM
tokens to ``device_tokens/{uid}``. This scheduled Modal job scans for notifications
that haven't been pushed yet, sends them via Firebase Cloud Messaging, and marks
them ``pushed=true``. No app change and no Firebase Blaze plan required.

Setup (one time):
    # Firebase console -> Project settings -> Service accounts -> Generate new private key
    modal secret create firebase-admin \
        FIREBASE_SERVICE_ACCOUNT="$(cat serviceAccount.json)" \
        FIREBASE_DB_URL=https://<your-project>-default-rtdb.firebaseio.com

Deploy:
    modal deploy sahara_push/modal_deploy.py
"""
from __future__ import annotations

import os
import modal

APP_NAME = "sahara-push"

image = modal.Image.debian_slim(python_version="3.11").pip_install("firebase-admin==6.5.0")
app = modal.App(name=APP_NAME, image=image)

SECRET = modal.Secret.from_name(
    "firebase-admin", required_keys=["FIREBASE_SERVICE_ACCOUNT", "FIREBASE_DB_URL"]
)


def _init():
    import json
    import firebase_admin
    from firebase_admin import credentials

    if not firebase_admin._apps:
        cred = credentials.Certificate(json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"]))
        firebase_admin.initialize_app(cred, {"databaseURL": os.environ["FIREBASE_DB_URL"]})


def _tokens_for(uid: str) -> list[str]:
    from firebase_admin import db

    raw = db.reference(f"device_tokens/{uid}").get() or {}
    out = []
    for v in raw.values():
        tok = v.get("token") if isinstance(v, dict) else v
        if tok:
            out.append(tok)
    return out


@app.function(image=image, secrets=[SECRET], schedule=modal.Period(minutes=1), timeout=120)
def push_pending() -> int:
    _init()
    from firebase_admin import db, messaging

    all_notifs = db.reference("user_notifications").get() or {}
    sent = 0
    for uid, items in all_notifs.items():
        if not isinstance(items, dict):
            continue
        tokens = _tokens_for(uid)
        for nid, n in items.items():
            if not isinstance(n, dict) or n.get("pushed"):
                continue
            marked = db.reference(f"user_notifications/{uid}/{nid}/pushed")
            if not tokens:
                marked.set(True)  # nothing to send to; don't retry forever
                continue
            title = n.get("titleEn") or "Sahara AI"
            body = n.get("bodyEn") or ""
            for tok in tokens:
                try:
                    messaging.send(
                        messaging.Message(
                            token=tok,
                            notification=messaging.Notification(title=title, body=body),
                            data={
                                "type": str(n.get("type", "")),
                                "actionRoute": str(n.get("actionRoute", "")),
                                "title": title,
                                "body": body,
                            },
                        )
                    )
                    sent += 1
                except Exception as e:  # dead/expired token, etc.
                    print(f"[sahara-push] send failed uid={uid} nid={nid}: {e}")
            marked.set(True)
    print(f"[sahara-push] pushed={sent}")
    return sent


@app.local_entrypoint()
def main():
    print("Deployed. Runs every minute. Test now with: modal run sahara_push/modal_deploy.py")
