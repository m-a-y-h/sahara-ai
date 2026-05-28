# Sahara Voice — Speech-Emotion Screening

Run the commands in this document from the repository's `services/` directory.

> **Scope.** Sahara Voice is a *screening* signal, not a diagnosis. The output
> is routed to humans (counselors, helpline operators), never used as a
> standalone clinical decision. Treat every ELEVATED / HIGH level the same way
> you would treat a self-reported "I'm not okay" check-in.

This package finalises a teammate's HuBERT-based voice-emotion classifier and
wires it into the same `/v1/lens/scan`-style screening API the Sahara Lens
camera flow uses, so the Android client can route NEUTRAL / ELEVATED / HIGH /
UNCERTAIN results through one shared UI.

The package supports **two label spaces** out of the box:

* **4-class Urdu (SEMOUR+)** — `anger`, `happiness`, `neutral`, `sadness`. This
  is what the upstream `Emotion-detection-from-audio-in-urdu` repo trains on.
  There is no `fear` class; the screening adapter handles that gracefully.
* **8-class RAVDESS** — `anger`, `calm`, `disgust`, `fearful`, `happy`,
  `neutral`, `sad`, `surprised`. Matches the teammate's published checkpoint.

The active label space is read at load time from a sibling `model_config.json`
(`{"id2label": {"0": "Anger", ...}, "num_classes": 8, "sample_rate": 16000,
"max_length": 96000}`), so a single binary works for both.

## Package layout

```
sahara_voice/
├── __init__.py        public surface re-exports
├── config.py          label spaces, screening thresholds, model defaults
├── model.py           HubertEmotionClassifier + checkpoint loader
├── preprocess.py      bytes → decode → denoise → resample → trim → pad
├── noise.py           background-noise reduction (improved over upstream)
├── screening.py       raw emotion probs → 4-class Sahara screening output
├── inference.py       VoiceInferenceEngine (process-global)
├── api.py             FastAPI router: /healthz, /analyze, /screen-from-probs
├── modal_deploy.py    Modal.com A10G deployment
├── requirements.txt
└── README.md
```

## What was finalised from the teammate's snippet

1. **LayerNorm instead of BatchNorm in the head.** BatchNorm1d crashes on
   batch size 1, which is what the inference API always sends. LayerNorm is
   the obvious fix; checkpoints saved with the BatchNorm head still load via
   `strict=False` and the LayerNorm weights re-initialise.
2. **Configurable backbone.** Upstream Urdu repo uses `hubert-large-ls960-ft`;
   teammate's snippet uses `hubert-base-ls960`. Both load with the same head,
   selected by `VoiceModelConfig.backbone`.
3. **Smarter noise profile.** The original implementation used the first
   0.5 s of audio as the noise profile, which fails when the user starts
   talking immediately. We now find the **quietest** 0.5 s window across the
   whole clip, fall back to stationary mode, and refuse to denoise clips
   shorter than 1 s where the profile would dominate the signal.
4. **Empty / short / unreadable input safety.** The pipeline pads
   too-short audio so HuBERT's conv stem doesn't collapse, returns a
   structured "preprocess failed" response (rather than 500) when
   `soundfile` / `librosa` can't decode, and never writes the audio to disk.
5. **Screening contract identical to Sahara Lens.** `screen_voice_emotions`
   returns the same `level / distress_score / screening_probs / reasons`
   shape that `sahara_lens.screening.screen_emotions` does, so the Android
   client renders both with the same components.

## Quickstart

### Install

```bash
# Full ML stack (training + serving)
pip install -r sahara_voice/requirements.txt

# Screening adapter only — enough for the unit tests
pip install numpy
```

### Lay out the checkpoint

```
sahara_voice/checkpoints/
├── best_model.pt        # state_dict (torch.save({"model_state_dict": ...}))
└── model_config.json    # {"id2label": {...}, "num_classes": N, ...}
```

### Serve

```bash
export SAHARA_VOICE_CHECKPOINT=sahara_voice/checkpoints/best_model.pt
export SAHARA_VOICE_MODEL_CONFIG=sahara_voice/checkpoints/model_config.json
uvicorn sahara_voice.api:app --host 0.0.0.0 --port 8001
```

Combine with Sahara AI under one FastAPI process:

```python
from fastapi import FastAPI
from sahara_voice.api import router as voice_router
from sahara_ai.app import app as chat_app

# Or build a new app and include both routers.
app = FastAPI()
app.include_router(voice_router, prefix="/v1/voice")
```

### Deploy on Modal

```bash
modal deploy sahara_voice/modal_deploy.py
modal run sahara_voice/modal_deploy.py::prewarm_weights
```

Modal prints a public HTTPS URL; paste it into `local.properties:sahara.voice.analyze.url`.

## `/v1/voice/analyze` contract

Request: `multipart/form-data` with a single `file` field (WAV / FLAC / OGG /
MP3 / M4A / WebM).

Response:

```json
{
  "passed": true,
  "model_version": "checkpoint:best_model.pt",
  "reasons": ["ok"],
  "audio": {"duration_s": 4.3, "sample_rate": 16000},
  "screening": {
    "level": "elevated",
    "distress_score": 0.58,
    "screening_probs": {"neutral": 0.42, "stress": 0.10, "sadness": 0.35, "fear": 0.13},
    "raw_probs": {"anger": 0.18, "calm": 0.05, "disgust": 0.03, "fearful": 0.13, "happy": 0.04, "neutral": 0.10, "sad": 0.35, "surprised": 0.12},
    "top_screening_class": "neutral",
    "top_screening_prob": 0.42,
    "reasons": ["aggregate distress score 0.58 ≥ elevated threshold 0.45"]
  }
}
```

## Limitations called out up front

* **No ASR.** This is *not* a speech-to-text model. Voice notes sent through
  this endpoint are scored for emotional state only — they are not transcribed
  and not fed back into the Sahara AI `/v1/chat` text prompt. (A future
  add-on Whisper integration could close that loop.)
* **4-class Urdu has no `fear`.** Checkpoints trained on SEMOUR+ produce a
  fear probability of 0 in the screening output. ELEVATED / HIGH still fires
  via the sadness + stress channels, but a fear-dominant clip won't be
  surfaced specifically.
* **Single voice note per request.** No batching, no streaming. Matches the
  privacy posture: one short clip, one screening result, no retention.
