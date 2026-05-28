# Local Connections Proof Of Concept

This local helper connects the Android Connections screen without deploying
Sahara. It currently supports Bluesky OAuth, Steam browser OpenID and Spotify
OAuth PKCE on one loopback server. YouTube authorization runs directly in the
Android app through Google Play services and does not use this helper.

For Bluesky, the OAuth request uses only the `atproto` scope. That verifies
account ownership and returns a DID. The helper immediately revokes its
development OAuth session because later analysis input is retrieved from
Bluesky's public `app.bsky.feed.getAuthorFeed` endpoint.

For Steam, browser OpenID verifies the user's SteamID. Steam does not provide
fine-grained OAuth permissions in this flow, so Sahara presents its own
consent disclosure. Reading visible profile or recently played-game activity
requires an optional Steam Web API key held by this helper, and remains subject
to the Steam user's privacy settings.

For Spotify, OAuth PKCE requests `user-read-private` only long enough to obtain
the linked account's Spotify ID and display name. Spotify is identity-only in
Sahara: no playlists, listening history, top tracks or recent playback are
retrieved or analyzed, because Spotify policy prohibits deriving health-related
profiles from Spotify content or the Spotify Service.

For YouTube, Android Google authorization requests `youtube.readonly`, the
scope used to retrieve the signed-in user's channel and subscriptions. With
explicit in-app consent, the app stores the channel ID/title and up to 1,000
subscribed channel IDs/titles as input for Sahara's later non-prescribed
drug-use risk indicator. It does not retrieve videos, comments or viewing
history in this flow. The Android record carries a 30-day expiry and is
deleted when the app next synchronizes after expiry; using Unlink also revokes
the granted YouTube scope.

## Run

Requirements:

- Node.js 22 or later
- `npm`
- `adb` for an Android device or emulator
- A Spotify developer app Client ID to test Spotify
- A Google Cloud Android OAuth client configured for the app to test YouTube

```bash
cd services/connections_poc_server
npm install
npm start
```

In a second terminal, make the phone/emulator's loopback port reach this
machine:

```bash
adb reverse tcp:8787 tcp:8787
```

If `adb` is not on `PATH` in this Android Studio setup, use:

```bash
~/Android/Sdk/platform-tools/adb reverse tcp:8787 tcp:8787
```

Run the Android app, sign in to Sahara, open Connections, and select a provider.
Bluesky redirects to `saharaai://oauth/bluesky/callback` and records a verified
DID and handle. Steam redirects to `saharaai://openid/steam/callback` and
records a verified SteamID after OpenID validation.

## Spotify Local Registration

Spotify requires app registration even for a localhost proof of concept:

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Create an app selecting the **Web API**.
3. In its redirect URI allowlist, add this exact URI:

```text
http://127.0.0.1:8787/oauth/spotify/callback
```

Spotify does not permit `http://localhost` redirect URIs; loopback IP literals
such as `127.0.0.1` are allowed for HTTP development callbacks.

Start the helper with its public Client ID:

```bash
SPOTIFY_CLIENT_ID=<your-client-id> npm start
```

No Spotify Client Secret is needed by this local PKCE flow and none should be
put in the Android app or its `BuildConfig`.

## YouTube Local Registration

YouTube requires a Google Cloud Android OAuth client even for local testing:

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create or select a project.
2. Enable the **YouTube Data API v3**.
3. Configure the OAuth consent screen; while the app is in testing, add the Google account used for the test as a test user.
4. Create an OAuth client with application type **Android**.
5. Enter the debug application identity used by this project:

```text
Package name: pk.edu.ucp.saharaai
SHA-1: D9:DA:EB:36:90:5F:67:5D:EB:1A:7D:C8:12:A6:D2:81:6A:9F:E9:E3
```

The SHA-1 shown is for the local debug signing key on this machine. A Play
Store release requires another Android client registered with the Play App
Signing SHA-1. No YouTube client secret or YouTube callback URL is needed by
the Android flow, and its Android client ID does not need to be placed in
`BuildConfig`.

If a different local port is needed, run the server with `PORT=<port>` and set
the Android `local.properties` value:

```properties
sahara.bluesky.poc.base.url=http://127.0.0.1:<port>
sahara.steam.poc.base.url=http://127.0.0.1:<port>
sahara.spotify.poc.base.url=http://127.0.0.1:<port>
```

Use the same port in the `adb reverse` command.

## Public Post Preview

After connecting an account, this endpoint returns text records suitable for a
later model input pipeline:

```bash
curl "http://127.0.0.1:8787/api/bluesky/posts?actor=HANDLE_OR_DID&limit=20"
```

It accesses public data only and performs no sentiment or substance-risk
prediction.

## Steam Activity Preview

Steam identity connection requires no API key. To request activity that the
user has made visible through Steam, obtain a user Web API key through Steam's
registration page and keep it on the helper process:

```bash
STEAM_WEB_API_KEY=<your-key> npm start
curl "http://127.0.0.1:8787/api/steam/activity?steamId=STEAM_ID64&count=20"
```

Do not place this key in `local.properties`, Android `BuildConfig`, Firebase,
or the APK. If the user's Steam privacy settings hide game details, the API
cannot supply that activity.

## Limits

This is a development proof of concept. The Android callback stores the
returned provider identity client-side, so it must not be treated as
tamper-resistant production verification. Production should complete provider
verification and persist the DID, SteamID, Spotify ID or YouTube channel ID on
a trusted backend after validating the signed-in Sahara user.

Spotify listening-data based wellness, mood or substance-risk prediction is
outside this implementation and should not be added under Spotify's current
Developer Policy.

YouTube subscriptions are stored only after the app explains their intended
use in Sahara's later risk-indicator prototype and receives explicit consent.
Before distributing a production app that computes cross-platform derived
scores, complete the applicable YouTube API compliance review.

For production, add a published privacy policy and YouTube Terms disclosure,
and enforce YouTube API-data refresh or deletion from a trusted backend. The
client-side expiry cleanup in this local prototype is not a production
retention control.
