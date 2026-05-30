"""FCM push + email sender — delivers SAHARA in-app and approval-flow notifications.

Three jobs run on the same cron (every minute):

  * ``push_pending``  — original. Scans ``user_notifications/{uid}/{id}`` for
    notifications that haven't been pushed yet and sends them via FCM to the
    user's saved device tokens.

  * ``notify_admins_of_pending`` — NEW. Scans ``registration_requests``,
    ``payment_requests``, and ``bug_reports`` for items that haven't yet been
    flagged with ``adminNotifSent=true``, then sends an FCM topic message to
    "admins" so any admin device that opened the dashboard once gets a real
    notification when a new approval lands. Stamps each item so admins aren't
    re-pinged about the same case.

  * ``deliver_keys`` — NEW. Scans ``key_deliveries`` for PENDING entries
    written by ``approveRegistrationRequest``, pushes the issued key to the
    applicant's saved FCM token (the app captures it at submission), AND
    sends an email containing the key. Flips the entry to SENT. Email needs
    SMTP env vars (see Setup); without them only the push is attempted.

Setup (one time):
    modal secret create firebase-admin \\
        FIREBASE_SERVICE_ACCOUNT="$(cat serviceAccount.json)" \\
        FIREBASE_DB_URL=https://<your-project>-default-rtdb.firebaseio.com

    # Optional — only needed for the deliver_keys email step:
    modal secret create sahara-mail \\
        SMTP_HOST=smtp.gmail.com SMTP_PORT=587 \\
        SMTP_USER=<your@gmail> SMTP_PASS=<gmail app password> \\
        EMAIL_FROM="Sahara AI <your@gmail>"

Deploy:
    modal deploy sahara_push/modal_deploy.py
"""
from __future__ import annotations

import os
import modal

APP_NAME = "sahara-push"

image = modal.Image.debian_slim(python_version="3.11").pip_install("firebase-admin==6.5.0")
app = modal.App(name=APP_NAME, image=image)

# Firebase service account + RTDB URL — required.
FB_SECRET = modal.Secret.from_name(
    "firebase-admin", required_keys=["FIREBASE_SERVICE_ACCOUNT", "FIREBASE_DB_URL"]
)

# SMTP — used only by deliver_keys. Create the secret BEFORE deploying, even if
# you don't have an SMTP provider yet: an empty value just means "skip the
# email step". When you have a provider, modal secret create --update with the
# real values and redeploy:
#
#     modal secret create sahara-mail \
#         SMTP_HOST=smtp.gmail.com SMTP_PORT=587 \
#         SMTP_USER=<your@gmail> SMTP_PASS=<gmail app password> \
#         EMAIL_FROM="Sahara AI <your@gmail>"
#
# If you really cannot make sahara-mail yet, comment MAIL_SECRET out of the
# deliver_keys decorator below.
MAIL_SECRET = modal.Secret.from_name("sahara-mail")

ADMIN_TOPIC = "admins"


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


# ----------------------------------------------------------------------------
# Job 1 — existing: deliver user_notifications/{uid}/{id} to user device tokens
# ----------------------------------------------------------------------------
@app.function(image=image, secrets=[FB_SECRET], schedule=modal.Period(minutes=1), timeout=120)
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
                except Exception as e:
                    print(f"[sahara-push] send failed uid={uid} nid={nid}: {e}")
            marked.set(True)
    print(f"[sahara-push] pushed={sent}")
    return sent


# ----------------------------------------------------------------------------
# Job 2 — NEW: ping the "admins" FCM topic when a new approval-needing item lands
# ----------------------------------------------------------------------------
def _notify_admins(messaging, title: str, body: str, data: dict) -> bool:
    try:
        messaging.send(
            messaging.Message(
                topic=ADMIN_TOPIC,
                notification=messaging.Notification(title=title, body=body),
                data={k: str(v) for k, v in data.items()},
            )
        )
        return True
    except Exception as e:
        print(f"[sahara-push] admin topic send failed: {e}")
        return False


@app.function(image=image, secrets=[FB_SECRET], schedule=modal.Period(minutes=1), timeout=120)
def notify_admins_of_pending() -> int:
    """Walk the three approval queues. For each item missing
    ``adminNotifSent=true`` AND in a not-yet-resolved status, fire one topic
    notification to ``admins`` and mark the item so we don't re-ping."""
    _init()
    from firebase_admin import db, messaging

    sent = 0

    # Registrations — flag anything that is PENDING_REVIEW.
    for rid, row in (db.reference("registration_requests").get() or {}).items():
        if not isinstance(row, dict) or row.get("adminNotifSent"):
            continue
        status = (row.get("status") or "").upper()
        if status and status != "PENDING_REVIEW":
            db.reference(f"registration_requests/{rid}/adminNotifSent").set(True)
            continue
        kind = (row.get("applicantType") or "applicant").title()
        name = row.get("applicantName") or row.get("organizationName") or "Unknown applicant"
        ok = _notify_admins(
            messaging,
            title=f"New {kind} application",
            body=f"{name} is awaiting your approval.",
            data={"type": "REGISTRATION", "actionRoute": "admin-dashboard", "requestId": rid},
        )
        if ok:
            sent += 1
            db.reference(f"registration_requests/{rid}/adminNotifSent").set(True)

    # Payment proofs — flag anything that is PENDING_REVIEW.
    for pid, row in (db.reference("payment_requests").get() or {}).items():
        if not isinstance(row, dict) or row.get("adminNotifSent"):
            continue
        status = (row.get("status") or "").upper()
        if status and status != "PENDING_REVIEW":
            db.reference(f"payment_requests/{pid}/adminNotifSent").set(True)
            continue
        amt = row.get("amountPkr") or "?"
        cname = row.get("counselorName") or "Counselor"
        ok = _notify_admins(
            messaging,
            title="New payment proof",
            body=f"PKR {amt} for {cname} — awaiting your review.",
            data={"type": "PAYMENT", "actionRoute": "admin-dashboard", "paymentId": pid},
        )
        if ok:
            sent += 1
            db.reference(f"payment_requests/{pid}/adminNotifSent").set(True)

    # Bug reports — flag anything that is not RESOLVED.
    for bid, row in (db.reference("bug_reports").get() or {}).items():
        if not isinstance(row, dict) or row.get("adminNotifSent"):
            continue
        status = (row.get("status") or "").upper()
        if status == "RESOLVED":
            db.reference(f"bug_reports/{bid}/adminNotifSent").set(True)
            continue
        ok = _notify_admins(
            messaging,
            title="New bug report",
            body=(row.get("deviceModel") or "A user") + " filed a bug report.",
            data={"type": "BUG", "actionRoute": "admin-dashboard", "reportId": bid},
        )
        if ok:
            sent += 1
            db.reference(f"bug_reports/{bid}/adminNotifSent").set(True)

    print(f"[sahara-push] admins pinged={sent}")
    return sent


# ----------------------------------------------------------------------------
# Job 3 — NEW: when admin approves, push + email the issued key to the applicant
# ----------------------------------------------------------------------------
def _send_email(to_addr: str, subject: str, body: str) -> bool:
    """Best-effort email send via SMTP env. Returns True if delivered, False
    otherwise. Missing env (no SMTP_HOST) is a clean "skip", not a failure."""
    host = os.environ.get("SMTP_HOST", "").strip()
    if not host or not to_addr:
        return False
    import smtplib
    from email.message import EmailMessage

    port = int(os.environ.get("SMTP_PORT", "587"))
    user = os.environ.get("SMTP_USER", "")
    pwd  = os.environ.get("SMTP_PASS", "")
    from_addr = os.environ.get("EMAIL_FROM") or user or "noreply@sahara.local"

    msg = EmailMessage()
    msg["From"]    = from_addr
    msg["To"]      = to_addr
    msg["Subject"] = subject
    msg.set_content(body)

    try:
        with smtplib.SMTP(host, port, timeout=15) as s:
            s.starttls()
            if user and pwd:
                s.login(user, pwd)
            s.send_message(msg)
        return True
    except Exception as e:
        print(f"[sahara-push] email send failed to={to_addr}: {e}")
        return False


@app.function(
    image=image,
    secrets=[FB_SECRET, MAIL_SECRET],
    schedule=modal.Period(minutes=1),
    timeout=120,
)
def deliver_keys() -> int:
    """Pick up PENDING entries written by approveRegistrationRequest, push the
    key to the applicant's stored FCM token, email it, then flag as SENT."""
    _init()
    from firebase_admin import db, messaging

    sent = 0
    for did, row in (db.reference("key_deliveries").get() or {}).items():
        if not isinstance(row, dict):
            continue
        status = (row.get("status") or "").upper()
        if status != "PENDING":
            continue
        kind  = (row.get("applicantType") or "applicant").upper()
        name  = row.get("applicantName") or ""
        email = (row.get("applicantEmail") or "").strip()
        token = (row.get("applicantToken") or "").strip()
        key   = (row.get("issuedKey") or "").strip()
        notes = (row.get("reviewNotes") or "").strip()
        if not key:
            db.reference(f"key_deliveries/{did}/status").set("ERROR_NO_KEY")
            continue

        body_lines = [
            f"Hello {name or 'there'},",
            "",
            f"Your Sahara AI {kind.title()} application has been approved.",
            f"Your access key is: {key}",
            "",
            "Open the Sahara AI app and enter this key on the Welcome > Settings screen "
            "to access your dashboard.",
        ]
        if notes:
            body_lines.extend(["", f"Admin notes: {notes}"])
        body_lines.extend(["", "— Sahara AI"])
        body = "\n".join(body_lines)

        push_ok = False
        if token:
            try:
                messaging.send(
                    messaging.Message(
                        token=token,
                        notification=messaging.Notification(
                            title=f"Sahara AI {kind.title()} key issued",
                            body=f"Your access key: {key}",
                        ),
                        data={
                            "type": "KEY_ISSUED",
                            "applicantType": kind,
                            "issuedKey": key,
                            "actionRoute": "welcome-settings",
                        },
                    )
                )
                push_ok = True
            except Exception as e:
                print(f"[sahara-push] key push failed delivery={did}: {e}")

        email_ok = _send_email(
            to_addr=email,
            subject=f"Your Sahara AI {kind.title()} access key",
            body=body,
        )

        delivered_at = int(__import__("time").time() * 1000)
        db.reference(f"key_deliveries/{did}").update({
            "status":      "SENT",
            "deliveredAt": delivered_at,
            "pushOk":      push_ok,
            "emailOk":     email_ok,
        })
        sent += 1

    print(f"[sahara-push] keys delivered={sent}")
    return sent


@app.local_entrypoint()
def main():
    print(
        "Deployed.\n"
        "  push_pending             — user_notifications -> user device tokens (every 1m)\n"
        "  notify_admins_of_pending — new approvals -> 'admins' topic (every 1m)\n"
        "  deliver_keys             — key_deliveries -> applicant push + email (every 1m)\n"
        "Test now with: modal run sahara_push/modal_deploy.py"
    )
