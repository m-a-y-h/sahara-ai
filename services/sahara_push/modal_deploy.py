"""FCM push + email sender — delivers SAHARA in-app and approval-flow notifications.

Four jobs run on the same cron (every minute), plus one direct mail endpoint:

  * ``push_pending``  — original. Scans ``user_notifications/{uid}/{id}`` for
    notifications that haven't been pushed yet and sends them via FCM to the
    user's saved device tokens.

  * ``notify_admins_of_pending`` — NEW. Scans ``registration_requests``,
    ``payment_requests``, and ``bug_reports`` for items that haven't yet been
    flagged with ``adminNotifSent=true``, then sends an FCM topic message to
    "admins" so any admin device that opened the dashboard once gets a real
    notification when a new approval lands. Stamps each item so admins aren't
    re-pinged about the same case.

  * ``send_application_acknowledgements`` — NEW. Scans new registration
    requests marked ``ackEmailStatus=PENDING`` and emails the applicant that
    their documents are in review.

  * ``deliver_registration_rejections`` — NEW. Scans
    ``registration_rejection_deliveries`` for PENDING entries written when an
    admin rejects an application and emails the applicant with the review note.

  * ``deliver_keys`` — NEW. Scans ``key_deliveries`` for PENDING entries
    written by ``approveRegistrationRequest``, pushes the issued key to the
    applicant's saved FCM token (the app captures it at submission), AND
    sends an email containing the key. Flips the entry to SENT. Email needs
    SMTP env vars (see Setup); without them only the push is attempted.

  * ``send_otp_email`` — direct HTTPS endpoint. The Android app calls this
    during signup verification so OTPs use the same SAHARA AI SMTP sender
    as the rest of the app. The endpoint verifies the caller's Firebase ID
    token before sending.

  * ``biometric_enroll`` / ``biometric_login`` / ``biometric_disable`` —
    direct HTTPS endpoints for per-device biometric login. The app stores a
    random device secret locally; this service stores only its hash and mints a
    Firebase custom token after validation.

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

import hashlib
import hmac
import os
import time
import modal

APP_NAME = "sahara-push"

image = modal.Image.debian_slim(python_version="3.11").pip_install(
    "firebase-admin==6.5.0",
    "fastapi[standard]==0.115.6",
)
app = modal.App(name=APP_NAME, image=image)

# Firebase service account + RTDB URL — required.
FB_SECRET = modal.Secret.from_name(
    "firebase-admin", required_keys=["FIREBASE_SERVICE_ACCOUNT", "FIREBASE_DB_URL"]
)

# SMTP — used by application acknowledgements and key delivery. Create the
# secret BEFORE deploying, even if you don't have an SMTP provider yet: an empty
# value just means "skip the email step". When you have a provider,
# modal secret create --update with the real values and redeploy:
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


def _sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _device_key(device_id: str) -> str:
    clean = (device_id or "").strip()
    if not clean:
        from fastapi import HTTPException
        raise HTTPException(status_code=400, detail="Missing device id.")
    return _sha256_hex(clean)


def _now_ms() -> int:
    return int(time.time() * 1000)


def _verify_id_token(id_token: str) -> dict:
    from fastapi import HTTPException
    from firebase_admin import auth as fb_auth

    clean = (id_token or "").strip()
    if not clean:
        raise HTTPException(status_code=400, detail="Missing Firebase token.")
    try:
        return fb_auth.verify_id_token(clean)
    except Exception as e:
        print(f"[sahara-push] token verification failed: {e}")
        raise HTTPException(status_code=401, detail="Invalid Firebase token.")


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
    configured_from = os.environ.get("EMAIL_FROM", "").strip()
    if configured_from:
        from_addr = configured_from
    elif user:
        from_addr = f"Sahara AI <{user}>"
    else:
        from_addr = "Sahara AI <noreply@sahara.local>"

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
    timeout=30,
    scaledown_window=300,
)
@modal.fastapi_endpoint(method="POST", label="sahara-mailer")
def send_otp_email(data: dict) -> dict:
    """Immediate transactional OTP sender for Android signup verification.
    The app supplies the signed-in Firebase user's ID token; we verify it here
    and only allow sending to that user's own email address."""
    _init()
    from fastapi import HTTPException
    from firebase_admin import auth as fb_auth

    id_token = (data.get("id_token") or "").strip()
    to_email = (data.get("to_email") or "").strip().lower()
    to_name = (data.get("to_name") or "").strip() or to_email.split("@")[0] or "there"

    if not id_token or not to_email:
        raise HTTPException(status_code=400, detail="Missing required email payload.")
    try:
        decoded = fb_auth.verify_id_token(id_token)
    except Exception as e:
        print(f"[sahara-push] token verification failed: {e}")
        raise HTTPException(status_code=401, detail="Invalid Firebase token.")

    token_email = (decoded.get("email") or "").strip().lower()
    if token_email != to_email:
        raise HTTPException(status_code=403, detail="Email does not match signed-in user.")

    otp_code = (data.get("otp_code") or "").strip()
    expiry_minutes = str(data.get("expiry_minutes") or "10").strip()
    if not otp_code:
        raise HTTPException(status_code=400, detail="Missing required email payload.")
    body = "\n".join([
        f"Hello {to_name},",
        "",
        f"Your Sahara AI verification code is: {otp_code}",
        "",
        f"This code expires in {expiry_minutes} minutes.",
        "If you did not request this, you can ignore this email.",
        "",
        "— Sahara AI",
    ])
    ok = _send_email(
        to_addr=to_email,
        subject="Your Sahara AI verification code",
        body=body,
    )
    if not ok:
        raise HTTPException(status_code=503, detail="Email sender is not configured or failed.")
    return {"ok": True}


@app.function(
    image=image,
    secrets=[FB_SECRET],
    timeout=30,
    scaledown_window=300,
)
@modal.fastapi_endpoint(method="POST", label="sahara-biometric-enroll")
def biometric_enroll(data: dict) -> dict:
    """Enroll one local device for biometric restore.

    Android sends a Firebase ID token plus a random device secret generated and
    stored locally behind Android Keystore. We store only the secret hash and
    use the device id hash as the RTDB key.
    """
    _init()
    from fastapi import HTTPException
    from firebase_admin import db

    decoded = _verify_id_token(data.get("id_token") or "")
    uid = (decoded.get("uid") or "").strip()
    if not uid:
        raise HTTPException(status_code=401, detail="Firebase token has no uid.")

    device_id = (data.get("device_id") or "").strip()
    device_secret = (data.get("device_secret") or "").strip()
    if not device_secret:
        raise HTTPException(status_code=400, detail="Missing device secret.")

    device_key = _device_key(device_id)
    now = _now_ms()
    email_hint = (data.get("email_hint") or decoded.get("email") or "").strip().lower()
    display_name_hint = (data.get("display_name_hint") or decoded.get("name") or "").strip()
    secret_hash = _sha256_hex(device_secret)

    db.reference().update({
        f"biometric_devices/{uid}/{device_key}": {
            "deviceIdHash": device_key,
            "secretHash": secret_hash,
            "enabled": True,
            "emailHint": email_hint,
            "displayNameHint": display_name_hint,
            "createdAt": now,
            "updatedAt": now,
            "lastUsedAt": 0,
        },
        f"biometric_device_index/{device_key}": uid,
        f"users/{uid}/biometricEnabled": True,
    })
    return {"ok": True}


@app.function(
    image=image,
    secrets=[FB_SECRET],
    timeout=30,
    scaledown_window=300,
)
@modal.fastapi_endpoint(method="POST", label="sahara-biometric-login")
def biometric_login(data: dict) -> dict:
    """Validate a device biometric credential and mint a Firebase custom token."""
    _init()
    from fastapi import HTTPException
    from firebase_admin import auth as fb_auth
    from firebase_admin import db

    device_id = (data.get("device_id") or "").strip()
    device_secret = (data.get("device_secret") or "").strip()
    if not device_secret:
        raise HTTPException(status_code=400, detail="Missing device secret.")

    device_key = _device_key(device_id)
    uid = db.reference(f"biometric_device_index/{device_key}").get()
    if not uid:
        raise HTTPException(status_code=404, detail="Biometric login is not enrolled on this device.")

    record = db.reference(f"biometric_devices/{uid}/{device_key}").get() or {}
    if not isinstance(record, dict):
        raise HTTPException(status_code=404, detail="Biometric login is not enrolled on this device.")
    if not record.get("enabled", False):
        raise HTTPException(status_code=403, detail="Biometric login is disabled for this device.")

    expected = (record.get("secretHash") or "").strip()
    actual = _sha256_hex(device_secret)
    if not expected or not hmac.compare_digest(expected, actual):
        raise HTTPException(status_code=401, detail="Biometric credential is invalid.")

    custom_token = fb_auth.create_custom_token(
        uid,
        {"biometric": True, "device_id": device_key},
    )
    if isinstance(custom_token, bytes):
        custom_token = custom_token.decode("utf-8")

    now = _now_ms()
    db.reference(f"biometric_devices/{uid}/{device_key}").update({
        "lastUsedAt": now,
        "updatedAt": now,
    })

    email = (record.get("emailHint") or "").strip().lower()
    display_name = (record.get("displayNameHint") or "").strip()
    try:
        user = fb_auth.get_user(uid)
        email = (user.email or email or "").strip().lower()
        display_name = (user.display_name or display_name or "").strip()
    except Exception as e:
        print(f"[sahara-push] biometric_login could not load user uid={uid}: {e}")

    return {
        "ok": True,
        "custom_token": custom_token,
        "email": email,
        "display_name": display_name,
    }


@app.function(
    image=image,
    secrets=[FB_SECRET],
    timeout=30,
    scaledown_window=300,
)
@modal.fastapi_endpoint(method="POST", label="sahara-biometric-disable")
def biometric_disable(data: dict) -> dict:
    """Disable this device's biometric restore credential for the signed-in UID."""
    _init()
    from fastapi import HTTPException
    from firebase_admin import db

    decoded = _verify_id_token(data.get("id_token") or "")
    uid = (decoded.get("uid") or "").strip()
    if not uid:
        raise HTTPException(status_code=401, detail="Firebase token has no uid.")

    device_key = _device_key(data.get("device_id") or "")
    now = _now_ms()
    db.reference().update({
        f"biometric_devices/{uid}/{device_key}/enabled": False,
        f"biometric_devices/{uid}/{device_key}/disabledAt": now,
        f"biometric_devices/{uid}/{device_key}/updatedAt": now,
        f"biometric_device_index/{device_key}": None,
    })

    devices = db.reference(f"biometric_devices/{uid}").get() or {}
    any_enabled = any(
        isinstance(device, dict) and device.get("enabled", False)
        for device in devices.values()
    )
    db.reference(f"users/{uid}/biometricEnabled").set(any_enabled)
    return {"ok": True}


@app.function(
    image=image,
    secrets=[FB_SECRET, MAIL_SECRET],
    schedule=modal.Period(minutes=1),
    timeout=120,
)
def send_application_acknowledgements() -> int:
    """Email applicants after the app has stored a new registration request.
    Only rows explicitly marked ``ackEmailStatus=PENDING`` are processed, so
    old pending requests are not emailed retroactively after deployment."""
    _init()
    import time
    from firebase_admin import db

    sent = 0
    smtp_ready = bool(os.environ.get("SMTP_HOST", "").strip())
    for rid, row in (db.reference("registration_requests").get() or {}).items():
        if not isinstance(row, dict):
            continue
        status = (row.get("status") or "").upper()
        ack_status = (row.get("ackEmailStatus") or "").upper()
        if status != "PENDING_REVIEW" or ack_status not in {"PENDING", "ERROR"}:
            continue
        attempts = int(row.get("ackEmailAttempts") or 0)
        if ack_status == "ERROR" and attempts >= 3:
            continue

        ref = db.reference(f"registration_requests/{rid}")
        now = int(time.time() * 1000)
        email = (row.get("email") or "").strip()
        if not email:
            ref.update({
                "ackEmailStatus": "SKIPPED_NO_EMAIL",
                "ackEmailLastAttemptAt": now,
            })
            continue
        if not smtp_ready:
            ref.update({
                "ackEmailStatus": "SKIPPED_NO_SMTP",
                "ackEmailLastAttemptAt": now,
            })
            continue

        kind = (row.get("applicantType") or "applicant").upper()
        name = row.get("applicantName") or row.get("organizationName") or "there"
        organization = (row.get("organizationName") or "").strip()
        display_name = organization if kind == "NGO" and organization else name
        body = "\n".join([
            f"Hello {display_name},",
            "",
            f"We have received your Sahara AI {kind.title()} application.",
            "Your documents are now waiting for review. Please stand by while the team verifies the information you submitted.",
            "",
            "We will email you again when your application is approved or if more information is needed.",
            "",
            "— Sahara AI",
        ])
        ok = _send_email(
            to_addr=email,
            subject=f"Sahara AI {kind.title()} application received",
            body=body,
        )
        ref.update({
            "ackEmailStatus": "SENT" if ok else "ERROR",
            "ackEmailAttempts": attempts + 1,
            "ackEmailLastAttemptAt": now,
            **({"ackEmailSentAt": now} if ok else {}),
        })
        if ok:
            sent += 1

    print(f"[sahara-push] application acknowledgements sent={sent}")
    return sent


@app.function(
    image=image,
    secrets=[FB_SECRET, MAIL_SECRET],
    schedule=modal.Period(minutes=1),
    timeout=120,
)
def deliver_registration_rejections() -> int:
    """Email applicants when an admin rejects a counselor/NGO application."""
    _init()
    import time
    from firebase_admin import db, messaging

    sent = 0
    smtp_ready = bool(os.environ.get("SMTP_HOST", "").strip())
    for did, row in (db.reference("registration_rejection_deliveries").get() or {}).items():
        if not isinstance(row, dict):
            continue
        status = (row.get("status") or "").upper()
        if status not in {"PENDING", "ERROR"}:
            continue
        attempts = int(row.get("attempts") or 0)
        if status == "ERROR" and attempts >= 3:
            continue

        ref = db.reference(f"registration_rejection_deliveries/{did}")
        now = int(time.time() * 1000)
        kind = (row.get("applicantType") or "applicant").upper()
        name = row.get("applicantName") or row.get("organizationName") or "there"
        email = (row.get("applicantEmail") or "").strip()
        token = (row.get("applicantToken") or "").strip()
        notes = (row.get("reviewNotes") or "").strip()

        if not email:
            ref.update({
                "status": "SKIPPED_NO_EMAIL",
                "lastAttemptAt": now,
                "attempts": attempts + 1,
            })
            continue
        if not smtp_ready:
            ref.update({
                "status": "SKIPPED_NO_SMTP",
                "lastAttemptAt": now,
                "attempts": attempts + 1,
            })
            continue

        body_lines = [
            f"Hello {name},",
            "",
            f"Your Sahara AI {kind.title()} application was reviewed, but it was not approved at this time.",
        ]
        if notes:
            body_lines.extend([
                "",
                f"Review note: {notes}",
                "",
                "You may submit a new request after correcting the issue above.",
            ])
        else:
            body_lines.extend([
                "",
                "No additional review note was provided.",
                "You may submit a new request with clearer or updated evidence.",
            ])
        body_lines.extend(["", "— Sahara AI"])
        body = "\n".join(body_lines)

        push_ok = False
        if token:
            try:
                messaging.send(
                    messaging.Message(
                        token=token,
                        notification=messaging.Notification(
                            title=f"Sahara AI {kind.title()} application update",
                            body="Your application was not approved. Check your email for details.",
                        ),
                        data={
                            "type": "REGISTRATION_REJECTED",
                            "applicantType": kind,
                            "actionRoute": "welcome-settings",
                        },
                    )
                )
                push_ok = True
            except Exception as e:
                print(f"[sahara-push] rejection push failed delivery={did}: {e}")

        email_ok = _send_email(
            to_addr=email,
            subject=f"Sahara AI {kind.title()} application update",
            body=body,
        )
        ref.update({
            "status": "SENT" if email_ok else "ERROR",
            "attempts": attempts + 1,
            "lastAttemptAt": now,
            "pushOk": push_ok,
            "emailOk": email_ok,
            **({"sentAt": now} if email_ok else {}),
        })
        if email_ok:
            sent += 1

    print(f"[sahara-push] registration rejections delivered={sent}")
    return sent


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
        "  send_application_acknowledgements — registration requests -> applicant email (every 1m)\n"
        "  deliver_registration_rejections — rejected registrations -> applicant email (every 1m)\n"
        "  deliver_keys             — key_deliveries -> applicant push + email (every 1m)\n"
        "  send_otp_email            — HTTPS OTP sender at / (direct)\n"
        "Test now with: modal run sahara_push/modal_deploy.py"
    )
