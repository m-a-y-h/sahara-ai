// One-time generator for the Sahara confidential-OAuth client's signing key.
//
// Run locally:
//     cd services/connections_poc_server
//     node scripts/generate-bluesky-key.mjs
//
// It prints a single-line JSON object — the PRIVATE JWK. Paste that value into
// BOTH places below, then redeploy:
//
//   1. Render → your sahara-connections service → Environment →
//      add env var BLUESKY_CLIENT_PRIVATE_JWK with the printed JSON
//   2. secrets/connections.env locally, same key=value, so adb-reverse
//      dev flows have the same identity as production
//
// Do NOT commit this value. secrets/ is already in .gitignore.
//
// The public half is derived from this private key on server start and served
// at /jwks.json. The atproto OAuth directory fetches the public part via
// /client-metadata.json's jwks_uri whenever a user starts a Bluesky auth
// flow — that's how your client is "registered". There's no Bluesky
// developer dashboard to visit.

import { webcrypto } from "node:crypto";

const { subtle } = webcrypto;

const { privateKey } = await subtle.generateKey(
  { name: "ECDSA", namedCurve: "P-256" },
  true,
  ["sign", "verify"],
);

const jwk = await subtle.exportKey("jwk", privateKey);
// Round out the JWK with atproto OAuth's required hints.
jwk.alg = "ES256";
jwk.use = "sig";
jwk.kid = "sahara-bsky-" + Date.now().toString(36);

console.log("");
console.log("=== Sahara Bluesky confidential-client private JWK ===");
console.log("Paste this exact line into env var  BLUESKY_CLIENT_PRIVATE_JWK");
console.log("on Render (Settings → Environment) AND into secrets/connections.env.");
console.log("Treat it like a password — never commit, never share.");
console.log("");
console.log(JSON.stringify(jwk));
console.log("");
console.log("kid =", jwk.kid);
console.log("=== End ===");
