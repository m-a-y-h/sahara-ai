# Firebase Support Files

This directory contains Firebase deployment support material, not Android
application source:

- `firestore.sahara-lens.rules.snippet`: rule fragment to merge into deployed
  Firestore rules when the Lens feature is enabled.
- `firestore.sahara-sleep.rules.snippet`: owner-only rule fragment for daily
  manual, Health Connect, estimated and fallback sleep records consumed by
  weekly risk analysis.

Do not bundle administrative seed data into the APK or rely on it as
production authorization.
