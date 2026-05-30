"""LiveKit token server for in-app voice/video calls.

The Android app (LiveKitTokenClient) POSTs to this endpoint and expects
``{"serverUrl": ..., "token": ...}``. The LiveKit API key/secret live ONLY here
(in a Modal Secret) — never in the app.

Setup (one time):
    modal secret create livekit \
        LIVEKIT_API_KEY=APIxxxx \
        LIVEKIT_API_SECRET=xxxx \
        LIVEKIT_URL=wss://<your>.livekit.cloud

Deploy:
    modal deploy sahara_livekit/modal_deploy.py

Then set in the app's local.properties:
    sahara.livekit.url=wss://<your>.livekit.cloud
    sahara.livekit.token.url=<the https URL Modal prints>/token
"""
# NOTE: do NOT add `from __future__ import annotations` here. It would turn the
# `req: TokenReq` route annotation into the string "TokenReq", which FastAPI then
# tries to resolve against this module's globals — but TokenReq is defined inside
# fastapi_app() (so pydantic stays a container-only import), so the lookup fails
# with `NameError: name 'TokenReq' is not defined` and the container crash-loops.

import os
import modal

APP_NAME = "sahara-livekit-token"

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install("fastapi==0.115.0", "pydantic==2.9.2", "livekit-api")
)
app = modal.App(name=APP_NAME, image=image)

SECRET = modal.Secret.from_name(
    "livekit", required_keys=["LIVEKIT_API_KEY", "LIVEKIT_API_SECRET", "LIVEKIT_URL"]
)


@app.function(image=image, secrets=[SECRET], scaledown_window=300, timeout=30)
@modal.asgi_app(label="livekit-token")
def fastapi_app():
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel
    from livekit import api

    web = FastAPI(title="Sahara LiveKit Token")

    class TokenReq(BaseModel):
        roomName: str
        identity: str
        displayName: str = ""
        mode: str = "voice"
        counselorKey: str = ""
        userId: str = ""

    @web.get("/healthz")
    def healthz():
        return {"ok": True}

    @web.post("/token")
    def token(req: TokenReq):
        if not req.roomName or not req.identity:
            raise HTTPException(status_code=400, detail="roomName and identity are required.")
        # SECURITY TODO: verify a Firebase ID token here and confirm the caller is a
        # participant of req.roomName before minting. Until then anyone who knows a
        # room name can mint a join token. Easy to add once the app sends the ID token.
        grant = api.VideoGrants(
            room_join=True, room=req.roomName, can_publish=True, can_subscribe=True
        )
        jwt = (
            api.AccessToken(os.environ["LIVEKIT_API_KEY"], os.environ["LIVEKIT_API_SECRET"])
            .with_identity(req.identity)
            .with_name(req.displayName or req.identity)
            .with_grants(grant)
            .to_jwt()
        )
        return {"serverUrl": os.environ["LIVEKIT_URL"], "token": jwt}

    return web
