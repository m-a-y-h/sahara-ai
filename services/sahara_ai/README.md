# Sahara AI Protocol

Run the commands in this document from the repository's `services/` directory.

This folder is the safety and app-contract layer for the Sahara AI fine-tuned LLM. It runs in front of the model on whichever host the team picks (Hugging Face Spaces, Google Cloud Run, a Firebase-Functions container, or a Colab GPU during research) and produces the `/v1/chat` JSON contract the Android app consumes.

The core rule: **do not rely on the model alone for overdose triage.** The protocol re-computes substance, risk, counselor trigger, UI card, and emergency destination before any model output is returned. The model still writes the user-facing reply when it meets the safety floor; deterministic Roman/Urdu/English text is only used when the model breaks JSON, invents a substance, gives unsafe instructions, or misses required overdose steps.

The Android app owns user workflow and Firebase access. This API receives the minimum chat payload, validates it, recomputes safety-critical fields, clamps the JSON response contract, and ignores any client-sent override of counselor triggers, risk level, or user data fields.

## Files

| File | Purpose |
|---|---|
| `sahara_ai_protocol.py` | Slang/risk parser, prompt builder, JSON extractor, safe-fallback replies, FastAPI route registration. |
| `regional_slang.py` | Curated regional slang alias extensions merged into the deterministic parser before model inference. |
| `app.py` | Deployment-agnostic FastAPI app for HF Spaces / Cloud Run / Firebase / Colab. Lazy model loading + health checks. |
| `generate_sahara_ai_sft_dataset.py` | Seed JSONL generator for supervised fine-tuning / LoRA. |
| `sahara_ai_sft_seed.jsonl` | Generated SFT seed examples (committed for reproducibility). |
| `../tests/test_sahara_ai_protocol.py` | Offline edge-case tests. |

## Coverage

The slang parser ships with substance profiles spanning the Pakistan substance-use landscape, not just the urban-Karachi slang most LLMs know:

- **Stimulants** — Ice/Methamphetamine, MDMA/Ecstasy, Cocaine/Crack, Captagon / synthetic cathinones / bath salts style NPS, plus misused stimulant/performance-enhancement products like modafinil, clenbuterol, ephedrine, DMAA/DMHA
- **Opioids** — Heroin/Opioids (incl. Balochistan/KPK street slang: *smack*, *black tar*, *brown powder*, *sufaid maal*); fentanyl analogs/nitazenes; Tramadol & unprescribed pain pills; Doda/Bhukki/Poppy husk (rural Punjab, Seraiki, Sindhi: *doda*, *bhukki*, *post*, *kuknar*, *tariyak*, *nattha*); atypical opioids such as kratom and tianeptine
- **Depressants** — Xanax/Benzodiazepines, Alcohol, GHB/GBL/1,4-BDO, Cough syrup / DXM / codeine, Pregabalin/Gabapentin, sedative-hypnotics/Z-drugs/barbiturates/muscle relaxants, and **Hooch/Kachi sharab/Tharra** (rural Punjab + Sindh + Balochistan illicit moonshine, treated separately because methanol contamination is a distinct ICU emergency)
- **Cannabis** — Cannabis/Charas with Androon-e-Lahore walled-city slang (*bottle wali*, *tola*, *tilla*, *boota*, *manori*, *majoon*, *thandai*, *phookni*, *tash*, *sulfa*), plus Synthetic cannabinoids / Spice / K2 as a separate high-risk profile
- **Dissociatives, deliriants & psychedelics** — Ketamine, PCP/Angel Dust, LSD/Psychedelics, Datura/Scopolamine
- **Inhalants** — Samad Bond, glue/petrol sniffing, thinner, nitrites/poppers, nitrous oxide
- **Nicotine** — Cigarettes/vape (Nicotine profile) plus a dedicated **Smokeless tobacco** profile for naswar, gutka, chaalia, mainpuri, mawa, khaini, paan masala
- **Research/performance compounds** — SARMs, peptides, racetams/nootropics, and other gray-market research chemicals when the user frames them as non-prescribed use or misuse
- **Pills** — Unknown / unprescribed pill catch-all

Qwen-supplied slang batches are treated as candidate data, not truth. The
parser only activates terms that are either already known, clinically useful
exact phrases, or safe weak matches requiring drug-use context. High-collision
terms such as ordinary verbs, names, and generic objects stay in the held-review
list until a stronger regional source is available.

The inference prompt also carries an informal-input rule for Pakistani youth
chat. The protocol passes Qalb both the raw user text and, when useful, a
conservative interpretation for Roman Urdu/no-vowel shorthand (`m`, `h`, `g`,
`kr`, `p`, `ue`, `xnx`, `trmdl`, `chrs`, `shrb`, etc.). This is backend context
only; the app does not visibly correct or rewrite the user's message.

## Out-of-scope: prescription pharma

A separate detection layer catches users asking about **legitimately prescribed medication** in a medical context (`my doctor prescribed sertraline`, `meri BP ki dawai`, `nuska`, `missed dose`, `interaction`, `for my thyroid`). In that case the route returns `user_intent = "prescription_inquiry_out_of_scope"`, `risk_level = "low"`, `message_type = "TEXT"`, and a localized reply telling the user to talk to their prescribing doctor or licensed pharmacist. This layer is intentionally conservative — it stays out of the way when overdose / misuse / craving / mixing cues are present, so a "doctor gave me Xanax and I took the whole strip" message is still routed as a critical event.

The drugs the prescription layer recognises include common Pakistani-prescribed antidepressants (sertraline, escitalopram, fluoxetine), antipsychotics, mood stabilisers, hypertension meds (amlodipine, losartan, ramipril), diabetes meds (metformin, insulin, gliclazide), thyroid (levothyroxine/eltroxin), antibiotics (augmentin, azee, cipro, flagyl), antihistamines, asthma inhalers, common OTC analgesics (panadol, brufen, disprin), and reproductive/hormonal medications.

## Deployment

The Android app now uses Firebase AI Logic's Gemini SDK directly for live chat, so there is no Sahara AI chat Modal proxy to deploy. Keep this package for protocol tests, service-style experiments, and model-evaluation work.

`sahara_ai/app.py` remains deployment-agnostic for research hosts that need the `/v1/chat` JSON contract. There is **no ngrok** anywhere in this repo — pick whichever managed host fits the budget:

### Hugging Face Spaces (recommended for FYP / research)

```text
sahara-ai-space/
├── Dockerfile
├── requirements.txt
└── app.py            # just: from sahara_ai.app import app
```

The Space exposes a public HTTPS URL for service-style clients that need the legacy `/v1/chat` contract.

### Google Cloud Run

```bash
gcloud run deploy sahara-ai \
    --source . \
    --port 8000 \
    --memory 16Gi \
    --cpu 4 \
    --gpu 1 --gpu-type=nvidia-l4 \
    --max-instances 1 \
    --region asia-south1 \
    --no-cpu-throttling \
    --set-env-vars="SAHARA_AI_MODEL_ID=enstazao/Sahara-AI-1.0-8B-Instruct"
```

### Firebase Cloud Functions (CPU-only smoke testing)

Wrap `sahara_ai.app:app` in a `functions_framework` HTTP function. Inference on CPU is slow but the deterministic safety layer always works, so the app degrades gracefully when the model is unavailable.

### Colab (research)

`sahara_ai.app:app` runs under `uvicorn` directly. Skip ngrok — use the public URL Colab exposes via `--share` Gradio for quick demos, or push the model to HF Hub and switch to the HF Spaces flow.

## Local smoke test

`test_connection.py` in `services/` sends a high-risk crisis payload to `http://localhost:8000/v1/chat` and prints the response. Start the app first:

```bash
uvicorn sahara_ai.app:app --host 0.0.0.0 --port 8000
```

Then in another shell:

```bash
python test_connection.py
```

## Local tests

```bash
python -m unittest tests/test_sahara_ai_protocol.py
```

## Generate seed fine-tuning data

```bash
python -m sahara_ai.generate_sahara_ai_sft_dataset --output sahara_ai/sahara_ai_sft_seed.jsonl
```

## `/v1/chat` contract

Request:

```json
{
  "user_input": "bhai mene boht zyada aiis pii li h ab saans ni ari help",
  "language": "roman_urdu"
}
```

Response (one of: critical overdose, high-risk mixing, medium substance support, out-of-scope prescription, anxiety, general):

```json
{
  "reply": "Ye stimulant/ice emergency ho sakti hai. Abhi 1122/115 call karein …",
  "trigger_counselor": true,
  "substance_detected": "Ice / Methamphetamine",
  "risk_level": "critical",
  "message_type": "CRISIS_CARD",
  "action_destination": "emergency",
  "quick_replies": ["Emergency kholo", "Trusted banda bulao", "Location bhejo"],
  "safety_flags": [],
  "detected_symptoms": ["breathing_distress", "severe_overdose_language"],
  "substances_detected": ["Ice / Methamphetamine"],
  "user_intent": "possible_overdose_or_medical_emergency"
}
```

The Android `ChatRepository` reads `reply`, `trigger_counselor`, `substance_detected`, `risk_level`, `message_type`, `action_destination`, and `quick_replies`.
