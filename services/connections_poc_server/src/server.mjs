import http from "node:http";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { requestLocalLock } from "@atproto/oauth-client";
import { NodeOAuthClient, buildAtprotoLoopbackClientMetadata } from "@atproto/oauth-client-node";
import { JoseKey } from "@atproto/jwk-jose";

// HOST: default 0.0.0.0 so Render's port scanner can reach us (it expects all
// interfaces). Local-dev `adb reverse tcp:8787 tcp:8787` still works against
// 0.0.0.0. Override with HOST=127.0.0.1 in your shell if you want loopback only.
const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT || 8787);
// BASE_URL must be the externally-reachable origin, not the bound host:port —
// OAuth callback URLs are registered with each platform against this string.
// On Render set BASE_URL=https://<service>.onrender.com in env vars; locally
// it falls back to the bound host:port for adb-reverse dev flows.
const BASE_URL = process.env.BASE_URL || `http://${HOST}:${PORT}`;
const SCOPE = "atproto";
const APP_CALLBACK = "saharaai://oauth/bluesky/callback";
const STEAM_OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
const STEAM_CALLBACK = "saharaai://openid/steam/callback";
const STEAM_WEB_API_KEY = process.env.STEAM_WEB_API_KEY ?? "";
const SPOTIFY_CLIENT_ID = process.env.SPOTIFY_CLIENT_ID ?? "";
const SPOTIFY_REDIRECT_URI = `${BASE_URL}/oauth/spotify/callback`;
const SPOTIFY_CALLBACK = "saharaai://oauth/spotify/callback";
const SPOTIFY_SCOPE = "user-read-private";

const stateStore = new Map();
const sessionStore = new Map();
const steamOpenIdRequests = new Map();
const spotifyRequests = new Map();

function mapStore(map) {
  return {
    async get(key) {
      return map.get(key);
    },
    async set(key, value) {
      map.set(key, value);
    },
    async del(key) {
      map.delete(key);
    }
  };
}

// Bluesky has three operating modes — chosen at startup based on BASE_URL
// and whether a private signing key is configured:
//
//   loopback     — http:// BASE_URL. Uses atproto's built-in loopback helper.
//                  Zero registration; convenient for local dev (adb reverse).
//                  http:// only — atproto's helper zod-rejects https.
//   confidential — https:// BASE_URL AND BLUESKY_CLIENT_PRIVATE_JWK env var
//                  is present. The server self-registers as a confidential
//                  OAuth client by serving its own client_metadata.json + a
//                  JWKS containing only the public key. The atproto OAuth
//                  directory fetches both URLs the first time a user starts
//                  a Bluesky auth flow on this server.
//                  Generate the keypair with: node scripts/generate-bluesky-key.mjs
//   disabled     — https:// BASE_URL with no key configured. The three
//                  Bluesky routes return a clean 503 with a setup hint.
const PRIVATE_JWK_JSON = (process.env.BLUESKY_CLIENT_PRIVATE_JWK || "").trim();
let bskyPrivateJwk = null;
let bskyPublicJwk = null;
if (PRIVATE_JWK_JSON) {
  try {
    bskyPrivateJwk = JSON.parse(PRIVATE_JWK_JSON);
    // Public JWK = private JWK minus the secret components. EC private fields
    // are 'd'; we strip the RSA private fields too for robustness in case
    // someone swaps key types later.
    bskyPublicJwk = { ...bskyPrivateJwk };
    for (const f of ["d", "p", "q", "dp", "dq", "qi", "oth", "k"]) delete bskyPublicJwk[f];
  } catch (e) {
    console.warn("[sahara-connections] BLUESKY_CLIENT_PRIVATE_JWK is not valid JSON:", e.message);
  }
}

let oauthClient = null;
let bskyMode = "disabled";

if (BASE_URL.startsWith("http://")) {
  oauthClient = new NodeOAuthClient({
    clientMetadata: buildAtprotoLoopbackClientMetadata({
      scope: SCOPE,
      redirect_uris: [`${BASE_URL}/oauth/bluesky/callback`],
    }),
    requestLock: requestLocalLock,
    stateStore: mapStore(stateStore),
    sessionStore: mapStore(sessionStore),
  });
  bskyMode = "loopback";
} else if (bskyPrivateJwk) {
  const CLIENT_ID = `${BASE_URL}/client-metadata.json`;
  const confidentialMetadata = {
    client_id: CLIENT_ID,
    client_name: "Sahara AI",
    client_uri: BASE_URL,
    application_type: "web",
    grant_types: ["authorization_code", "refresh_token"],
    scope: SCOPE,
    response_types: ["code"],
    redirect_uris: [`${BASE_URL}/oauth/bluesky/callback`],
    token_endpoint_auth_method: "private_key_jwt",
    token_endpoint_auth_signing_alg: "ES256",
    dpop_bound_access_tokens: true,
    jwks_uri: `${BASE_URL}/jwks.json`,
  };
  const signingKey = await JoseKey.fromImportable(bskyPrivateJwk);
  oauthClient = new NodeOAuthClient({
    clientMetadata: confidentialMetadata,
    keyset: [signingKey],
    requestLock: requestLocalLock,
    stateStore: mapStore(stateStore),
    sessionStore: mapStore(sessionStore),
  });
  bskyMode = "confidential";
}

if (bskyMode === "disabled") {
  console.warn(
    "[sahara-connections] Bluesky routes disabled. To enable on this HTTPS deploy, generate a keypair with " +
      "`node scripts/generate-bluesky-key.mjs` and paste the JSON into env var BLUESKY_CLIENT_PRIVATE_JWK.",
  );
}

function sendJson(response, status, body) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(JSON.stringify(body));
}

function sendHtml(response, status, title, body) {
  response.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(`<!doctype html>
<html lang="en">
  <head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${title}</title></head>
  <body style="font-family: sans-serif; max-width: 44rem; margin: 3rem auto; padding: 0 1rem; line-height: 1.5">
    <h1>${title}</h1>
    ${body}
  </body>
</html>`);
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function redirect(response, location) {
  response.writeHead(302, { Location: location, "Cache-Control": "no-store" });
  response.end();
}

function normalizeIdentifier(value) {
  return value.trim().replace(/^@/, "");
}

function errorMessage(error) {
  return error instanceof Error ? error.message : "Unknown authorization error";
}

function callbackWith(callbackUri, parameters) {
  const callback = new URL(callbackUri);
  for (const [key, value] of Object.entries(parameters)) {
    if (value) callback.searchParams.set(key, value);
  }
  return callback.toString();
}

function appCallbackWith(parameters) {
  return callbackWith(APP_CALLBACK, parameters);
}

function steamAppCallbackWith(parameters) {
  return callbackWith(STEAM_CALLBACK, parameters);
}

function spotifyAppCallbackWith(parameters) {
  return callbackWith(SPOTIFY_CALLBACK, parameters);
}

async function loadPublicProfile(did) {
  const endpoint = new URL("https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile");
  endpoint.searchParams.set("actor", did);
  const response = await fetch(endpoint);
  if (!response.ok) {
    throw new Error(`Bluesky profile request failed (${response.status})`);
  }
  return response.json();
}

async function loadPublicPosts(actor, limit) {
  const endpoint = new URL("https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed");
  endpoint.searchParams.set("actor", actor);
  endpoint.searchParams.set("limit", String(limit));
  endpoint.searchParams.set("filter", "posts_with_replies");
  const response = await fetch(endpoint);
  if (!response.ok) {
    throw new Error(`Bluesky feed request failed (${response.status})`);
  }
  const payload = await response.json();
  return {
    actor,
    posts: payload.feed.map((item) => ({
      uri: item.post?.uri ?? "",
      text: item.post?.record?.text ?? "",
      createdAt: item.post?.record?.createdAt ?? "",
      isReply: Boolean(item.reply)
    }))
  };
}

function validSteamId(steamId) {
  return /^\d{17,25}$/.test(steamId);
}

async function loadSteamProfile(steamId) {
  if (!STEAM_WEB_API_KEY) return null;
  const endpoint = new URL("https://partner.steam-api.com/ISteamUser/GetPlayerSummaries/v2/");
  endpoint.searchParams.set("steamids", steamId);
  const response = await fetch(endpoint, {
    headers: { "x-webapi-key": STEAM_WEB_API_KEY }
  });
  if (!response.ok) {
    throw new Error(`Steam player summary request failed (${response.status})`);
  }
  const payload = await response.json();
  return payload.response?.players?.[0] ?? null;
}

async function loadSteamActivity(steamId, count) {
  if (!STEAM_WEB_API_KEY) {
    throw new Error("STEAM_WEB_API_KEY is not configured on the local helper");
  }
  const endpoint = new URL("https://partner.steam-api.com/IPlayerService/GetRecentlyPlayedGames/v1/");
  endpoint.searchParams.set("input_json", JSON.stringify({ steamid: steamId, count }));
  const response = await fetch(endpoint, {
    headers: { "x-webapi-key": STEAM_WEB_API_KEY }
  });
  if (!response.ok) {
    throw new Error(`Steam activity request failed (${response.status})`);
  }
  const payload = await response.json();
  return payload.response ?? {};
}

function pkceValue(bytes = 32) {
  return randomBytes(bytes).toString("base64url");
}

function pkceChallenge(verifier) {
  return createHash("sha256").update(verifier).digest("base64url");
}

function startSpotifyOAuth(response) {
  if (!SPOTIFY_CLIENT_ID) {
    return sendHtml(
      response,
      503,
      "Spotify app registration required",
      `<p>Spotify requires a registered application even for local development.</p>
       <p>Create a Spotify Web API app, allowlist <code>${SPOTIFY_REDIRECT_URI}</code>,
       then restart this helper with <code>SPOTIFY_CLIENT_ID=&lt;client-id&gt;</code>.</p>`
    );
  }
  const state = pkceValue();
  const verifier = pkceValue(64);
  spotifyRequests.set(state, { verifier, createdAt: Date.now() });
  const endpoint = new URL("https://accounts.spotify.com/authorize");
  endpoint.searchParams.set("client_id", SPOTIFY_CLIENT_ID);
  endpoint.searchParams.set("response_type", "code");
  endpoint.searchParams.set("redirect_uri", SPOTIFY_REDIRECT_URI);
  endpoint.searchParams.set("scope", SPOTIFY_SCOPE);
  endpoint.searchParams.set("state", state);
  endpoint.searchParams.set("code_challenge_method", "S256");
  endpoint.searchParams.set("code_challenge", pkceChallenge(verifier));
  redirect(response, endpoint.toString());
}

async function completeSpotifyOAuth(url) {
  const state = url.searchParams.get("state") ?? "";
  const savedRequest = spotifyRequests.get(state);
  spotifyRequests.delete(state);
  if (!savedRequest || Date.now() - savedRequest.createdAt > 10 * 60 * 1000) {
    throw new Error("Spotify authorization request is missing or expired");
  }
  if (url.searchParams.has("error")) {
    throw new Error(`Spotify authorization failed: ${url.searchParams.get("error")}`);
  }
  const code = url.searchParams.get("code") ?? "";
  if (!code) {
    throw new Error("Spotify did not return an authorization code");
  }

  const tokenResponse = await fetch("https://accounts.spotify.com/api/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: SPOTIFY_CLIENT_ID,
      grant_type: "authorization_code",
      code,
      redirect_uri: SPOTIFY_REDIRECT_URI,
      code_verifier: savedRequest.verifier
    }).toString()
  });
  if (!tokenResponse.ok) {
    throw new Error(`Spotify token request failed (${tokenResponse.status})`);
  }
  const token = await tokenResponse.json();
  const profileResponse = await fetch("https://api.spotify.com/v1/me", {
    headers: { Authorization: `Bearer ${token.access_token}` }
  });
  if (!profileResponse.ok) {
    throw new Error(`Spotify profile request failed (${profileResponse.status})`);
  }
  const profile = await profileResponse.json();
  if (!profile.id) {
    throw new Error("Spotify did not return a valid account identity");
  }
  return {
    spotifyId: profile.id,
    displayName: profile.display_name ?? ""
  };
}

function startSteamOpenId(response) {
  const requestId = randomUUID();
  const returnTo = `${BASE_URL}/openid/steam/callback?request=${encodeURIComponent(requestId)}`;
  steamOpenIdRequests.set(requestId, Date.now());
  const endpoint = new URL(STEAM_OPENID_ENDPOINT);
  endpoint.searchParams.set("openid.ns", "http://specs.openid.net/auth/2.0");
  endpoint.searchParams.set("openid.mode", "checkid_setup");
  endpoint.searchParams.set("openid.return_to", returnTo);
  endpoint.searchParams.set("openid.realm", `${BASE_URL}/`);
  endpoint.searchParams.set("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
  endpoint.searchParams.set("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
  redirect(response, endpoint.toString());
}

async function verifySteamOpenId(url) {
  const requestId = url.searchParams.get("request") ?? "";
  const startedAt = steamOpenIdRequests.get(requestId);
  steamOpenIdRequests.delete(requestId);
  if (!startedAt || Date.now() - startedAt > 10 * 60 * 1000) {
    throw new Error("Steam sign-in request is missing or expired");
  }
  if (url.searchParams.get("openid.mode") === "cancel") {
    throw new Error("Steam sign-in was cancelled");
  }
  const expectedReturnTo = `${BASE_URL}/openid/steam/callback?request=${encodeURIComponent(requestId)}`;
  if (url.searchParams.get("openid.return_to") !== expectedReturnTo) {
    throw new Error("Steam sign-in returned an unexpected callback");
  }
  if (url.searchParams.get("openid.op_endpoint") !== STEAM_OPENID_ENDPOINT) {
    throw new Error("Steam sign-in provider could not be verified");
  }

  const verification = new URLSearchParams();
  for (const [key, value] of url.searchParams) {
    if (key.startsWith("openid.")) verification.set(key, value);
  }
  verification.set("openid.mode", "check_authentication");
  const response = await fetch(STEAM_OPENID_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: verification.toString()
  });
  const result = await response.text();
  if (!response.ok || !/(^|\n)is_valid:true(\n|$)/.test(result)) {
    throw new Error("Steam could not verify this sign-in response");
  }

  const claimedId = url.searchParams.get("openid.claimed_id") ?? "";
  const steamId = claimedId.match(/^https?:\/\/steamcommunity\.com\/openid\/id\/(\d{17,25})$/)?.[1] ?? "";
  if (!validSteamId(steamId)) {
    throw new Error("Steam did not return a valid SteamID");
  }
  return steamId;
}

async function handleRequest(request, response) {
  const url = new URL(request.url ?? "/", BASE_URL);

  if (request.method === "GET" && url.pathname === "/healthz") {
    return sendJson(response, 200, {
      ok: true,
      blueskyOAuthScope: SCOPE,
      steamOpenId: true,
      steamActivityConfigured: Boolean(STEAM_WEB_API_KEY),
      spotifyOAuth: true,
      spotifyConfigured: Boolean(SPOTIFY_CLIENT_ID),
      spotifyScope: SPOTIFY_SCOPE
    });
  }

  if (request.method === "GET" && url.pathname === "/oauth-client-metadata.json") {
    if (!oauthClient) {
      return sendJson(response, 503, {
        error: "bluesky-disabled",
        detail: "Set env var BLUESKY_CLIENT_PRIVATE_JWK on this deploy to enable Bluesky in confidential-client mode (see scripts/generate-bluesky-key.mjs).",
      });
    }
    return sendJson(response, 200, oauthClient.clientMetadata);
  }

  // Canonical atproto OAuth metadata endpoint. The client_metadata.json
  // returned here IS our registration — atproto's OAuth directory fetches
  // this URL whenever a user starts a Bluesky auth flow on this server.
  if (request.method === "GET" && url.pathname === "/client-metadata.json") {
    if (!oauthClient || bskyMode !== "confidential") {
      return sendJson(response, 503, {
        error: "bluesky-not-in-confidential-mode",
        mode: bskyMode,
        detail: "Confidential-client metadata is only published when BASE_URL is https:// AND BLUESKY_CLIENT_PRIVATE_JWK is configured.",
      });
    }
    return sendJson(response, 200, oauthClient.clientMetadata);
  }

  // Public JWKS — atproto fetches this from the jwks_uri in client_metadata
  // to verify our private_key_jwt client assertions. Only ever serves the
  // public half of the key (private 'd' / 'p' / 'q' fields were stripped at
  // startup); the private JWK in env never leaves this process.
  if (request.method === "GET" && url.pathname === "/jwks.json") {
    if (!bskyPublicJwk) {
      return sendJson(response, 503, {
        error: "bluesky-not-in-confidential-mode",
        mode: bskyMode,
      });
    }
    return sendJson(response, 200, { keys: [bskyPublicJwk] });
  }

  if (request.method === "GET" && url.pathname === "/") {
    return sendHtml(
      response,
      200,
      "Sahara local connections helper",
      `<p>The development provider-login helper is running.</p>
       <p>Bluesky requests only <code>atproto</code> identity verification; posts are fetched only
       from Bluesky's public API.</p>
       <p>Steam uses OpenID to verify SteamID. Visible game-activity preview is
       ${STEAM_WEB_API_KEY ? "enabled" : "disabled until <code>STEAM_WEB_API_KEY</code> is configured"}.</p>
       <p>Spotify uses OAuth PKCE for identity only. It is
       ${SPOTIFY_CLIENT_ID ? "configured" : "disabled until <code>SPOTIFY_CLIENT_ID</code> is configured"}.</p>`
    );
  }

  if (request.method === "GET" && url.pathname === "/oauth/bluesky/start") {
    if (!oauthClient) {
      return sendHtml(response, 503, "Bluesky disabled on this deploy",
        "<p>This Sahara connections helper is hosted on HTTPS, but the Bluesky atproto loopback client only works on http://. " +
        "Run the helper locally with <code>adb reverse</code> for Bluesky testing, or wire a confidential client to enable Bluesky in production.</p>");
    }
    const identifier = normalizeIdentifier(url.searchParams.get("handle") ?? "");
    if (!identifier || !identifier.includes(".")) {
      return sendHtml(response, 400, "Invalid Bluesky handle", "<p>Return to Sahara and enter a full handle such as user.bsky.social.</p>");
    }
    try {
      const authorizationUrl = await oauthClient.authorize(identifier, { scope: SCOPE });
      return redirect(response, authorizationUrl.toString());
    } catch (error) {
      return sendHtml(
        response,
        502,
        "Unable to start Bluesky authorization",
        `<p>${escapeHtml(errorMessage(error))}</p><p>Return to Sahara and try again.</p>`
      );
    }
  }

  if (request.method === "GET" && url.pathname === "/oauth/bluesky/callback") {
    if (!oauthClient) {
      return sendHtml(response, 503, "Bluesky disabled on this deploy",
        "<p>This Sahara connections helper is hosted on HTTPS, but the Bluesky atproto loopback client only works on http://. " +
        "Run the helper locally with <code>adb reverse</code> for Bluesky testing.</p>");
    }
    try {
      const { session } = await oauthClient.callback(url.searchParams);
      const profile = await loadPublicProfile(session.did);

      // Public post access does not need an OAuth session. End the identity-only
      // development session after ownership is verified.
      await oauthClient.revoke(session.did).catch(() => undefined);
      sessionStore.delete(session.did);

      return redirect(response, appCallbackWith({
        did: session.did,
        handle: profile.handle
      }));
    } catch (error) {
      return redirect(response, appCallbackWith({ error: errorMessage(error) }));
    }
  }

  if (request.method === "GET" && url.pathname === "/api/bluesky/posts") {
    const actor = normalizeIdentifier(url.searchParams.get("actor") ?? "");
    const requestedLimit = Number(url.searchParams.get("limit") || 20);
    const limit = Number.isFinite(requestedLimit) ? Math.min(Math.max(requestedLimit, 1), 100) : 20;
    if (!actor) {
      return sendJson(response, 400, { error: "actor is required" });
    }
    try {
      return sendJson(response, 200, await loadPublicPosts(actor, limit));
    } catch (error) {
      return sendJson(response, 502, { error: errorMessage(error) });
    }
  }

  if (request.method === "GET" && url.pathname === "/openid/steam/start") {
    return startSteamOpenId(response);
  }

  if (request.method === "GET" && url.pathname === "/openid/steam/callback") {
    try {
      const steamId = await verifySteamOpenId(url);
      const profile = await loadSteamProfile(steamId).catch(() => null);
      return redirect(response, steamAppCallbackWith({
        steamId,
        displayName: profile?.personaname ?? ""
      }));
    } catch (error) {
      return redirect(response, steamAppCallbackWith({ error: errorMessage(error) }));
    }
  }

  if (request.method === "GET" && url.pathname === "/api/steam/activity") {
    const steamId = url.searchParams.get("steamId") ?? "";
    const requestedCount = Number(url.searchParams.get("count") || 20);
    const count = Number.isFinite(requestedCount) ? Math.min(Math.max(requestedCount, 1), 100) : 20;
    if (!validSteamId(steamId)) {
      return sendJson(response, 400, { error: "A valid SteamID is required" });
    }
    if (!STEAM_WEB_API_KEY) {
      return sendJson(response, 501, {
        error: "Set STEAM_WEB_API_KEY on the local helper to preview visible Steam activity"
      });
    }
    try {
      return sendJson(response, 200, {
        steamId,
        profile: await loadSteamProfile(steamId),
        recentActivity: await loadSteamActivity(steamId, count)
      });
    } catch (error) {
      return sendJson(response, 502, { error: errorMessage(error) });
    }
  }

  if (request.method === "GET" && url.pathname === "/oauth/spotify/start") {
    return startSpotifyOAuth(response);
  }

  if (request.method === "GET" && url.pathname === "/oauth/spotify/callback") {
    try {
      return redirect(response, spotifyAppCallbackWith(await completeSpotifyOAuth(url)));
    } catch (error) {
      return redirect(response, spotifyAppCallbackWith({ error: errorMessage(error) }));
    }
  }

  sendJson(response, 404, { error: "Not found" });
}

const server = http.createServer((request, response) => {
  handleRequest(request, response).catch((error) => {
    sendJson(response, 500, { error: errorMessage(error) });
  });
});

server.listen(PORT, HOST, () => {
  console.log(`Sahara local connections POC listening at ${BASE_URL}`);
  console.log(`For Android over USB/emulator, run: adb reverse tcp:${PORT} tcp:${PORT}`);
  console.log(`Bluesky OAuth scope: ${SCOPE} (identity verification only)`);
  console.log(`Steam OpenID enabled; activity preview: ${STEAM_WEB_API_KEY ? "enabled" : "requires STEAM_WEB_API_KEY"}`);
  console.log(`Spotify OAuth PKCE identity link: ${SPOTIFY_CLIENT_ID ? "enabled" : "requires SPOTIFY_CLIENT_ID"}`);
});
