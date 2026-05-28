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
- `tests/`: Python tests for the services.
