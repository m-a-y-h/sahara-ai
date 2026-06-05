# Sahara Lens — Facial Emotion Recognition for Distress Screening

Run the commands in this document from the repository's `services/` directory.

> **Scope.** Sahara Lens is a *screening* model. Outputs are routed to humans
> (counselors, helpline operators), never used as a diagnosis. Treat every
> elevated screening level the same way you would treat a self-reported "I'm
> not okay" check-in: take it seriously, but verify with a human in the loop.

This package contains the ML model and serving stack that powers the
camera-based check-in flow in the SAHARA AI Android app. It classifies a
single, well-aligned face image into the negative-emotion cluster (stress,
sadness, fear) associated with non-prescribed drug use distress in the
South Asian (KPK, Punjab, Sindh, Balochistan) deployment context.

The strategy follows the research synthesis bundled in the top-level
`README.md`:

1. **Architecture.** Hybrid ResNet-50 + small Vision Transformer head.
   The CNN trunk extracts robust local features; the transformer re-attends
   over them globally. The implementation is in `model.py` and is dependency-
   light (torch + torchvision only — no `timm`).
2. **Dataset.** Primary: [InFER++][inferpp] (real-world multi-ethnic Indian
   subcontinent FER dataset). Secondary candidates: ISED, ISSED, AIIMS
   Facial Toolbox, RAF-DB. The `dataset.py` loader accepts the standard
   `ImageFolder` layout and supports per-deployment folder-name aliases.
3. **Training discipline.** Two-group AdamW (lower LR for backbone, higher
   for head), cosine schedule with linear warmup, class-balanced sampling
   *and* class-weighted CE, RandAugment + Mixup, label smoothing. Early
   stopping on the **ship-gate metric**: minimum per-class F1 across
   (stress, sadness, fear) in the 4-class screening space.
4. **Ethics & safety.** No image is persisted on the server. The endpoint
   refuses degenerate captures via a server-side quality gate
   (`quality_gate.py`). Outputs always include `model_version` and a
   `reasons` list so the counselor dashboard can show provenance.

[inferpp]: https://www.researchgate.net/publication/383135370_InFER_Real-World_Indian_Facial_Expression_Dataset

---

## Package layout

```
sahara_lens/
├── __init__.py          public surface re-exports
├── config.py            label spaces, model/train/screening dataclasses
├── model.py             HybridResNetViT
├── transforms.py        train + eval image transforms, mixup
├── dataset.py           EmotionImageFolder + class-balanced sampler/weights
├── train.py             fine-tuning loop (run with `python -m sahara_lens.train`)
├── evaluate.py          per-class F1, confusion matrices, subgroup audits
├── screening.py         raw-emotion → 4-class screening mapping (no torch)
├── quality_gate.py      server-side image quality validator (no torch)
├── inference.py         single-image inference engine + process-global engine
├── api.py               FastAPI router: GET /healthz, POST /scan, POST /screen-from-probs
├── requirements.txt
└── README.md
```

## Quickstart

### 1. Install

```bash
# Full ML stack (training + serving)
pip install -r sahara_lens/requirements.txt

# Screening logic only — enough to run the unit tests
pip install pillow numpy
```

### 2. Lay out the dataset

```
data/inferpp/
├── train/
│   ├── anger/ *.jpg
│   ├── disgust/ *.jpg
│   ├── fear/ *.jpg
│   ├── happiness/ *.jpg
│   ├── neutral/ *.jpg
│   ├── sadness/ *.jpg
│   └── surprise/ *.jpg
├── val/    (same subfolders)
└── test/   (optional — same subfolders)
```

If your dataset uses different folder names (e.g. `happy`, `angry`) pass a
JSON alias map to `--class-aliases`.

### 3. Fine-tune

```bash
python -m sahara_lens.train \
    --data-root data/inferpp \
    --output-dir sahara_lens/checkpoints/run_01 \
    --epochs 60 \
    --batch-size 32
```

The script saves the best checkpoint by ship-gate metric to
`<output-dir>/best.pt`, an append-only `<output-dir>/history.jsonl`, and
final test metrics to `<output-dir>/test_metrics.json`.

### 4. Serve

```bash
export SAHARA_LENS_CHECKPOINT=sahara_lens/checkpoints/run_01/best.pt
uvicorn sahara_lens.api:app --host 0.0.0.0 --port 8000
```

To mount the Lens router inside another FastAPI process:

```python
from fastapi import FastAPI
from sahara_lens.api import router as lens_router

app = FastAPI()
app.include_router(lens_router, prefix="/v1/lens")
```

Point the Android app at the resulting endpoint in `local.properties`:

```properties
sahara.lens.scan.url=https://your-host/v1/lens/scan
```

### 5. Test from the command line

```bash
curl -F "file=@./test_face.jpg" http://localhost:8000/v1/lens/scan
```

Sample response:

```json
{
  "passed": true,
  "model_version": "checkpoint:best.pt",
  "quality_gate": {
    "passed": true,
    "reasons": ["all checks passed"],
    "metrics": {"brightness": 142.3, "contrast": 51.8, "sharpness": 312.6, "face_fraction": 0.34},
    "face_box": [220, 180, 240, 240],
    "face_detector": "opencv-haar-frontalface"
  },
  "screening": {
    "level": "elevated",
    "distress_score": 0.61,
    "screening_probs": {"neutral": 0.39, "stress": 0.22, "sadness": 0.30, "fear": 0.09},
    "raw_probs": {"anger": 0.13, "disgust": 0.10, "fear": 0.06, "happiness": 0.18, "neutral": 0.08, "sadness": 0.34, "surprise": 0.11},
    "top_screening_class": "neutral",
    "top_screening_prob": 0.39,
    "reasons": ["aggregate distress score 0.61 ≥ elevated threshold 0.45"]
  }
}
```

---

## Ship gate

Top-1 accuracy is **not** the metric this model ships on. From the research
synthesis: many published FER models reach 90%+ aggregate accuracy while
collapsing on negative-emotion classes. So the trainer optimises and
early-stops on:

```text
ship_gate = min(
    F1_screening("stress"),
    F1_screening("sadness"),
    F1_screening("fear"),
)
```

A run is shippable when ship_gate ≥ 0.92 on the held-out validation split.
Until then, it stays in the `checkpoints/` directory and the Android app
keeps `sahara.lens.scan.url` blank, which makes the client treat the lens
endpoint as unavailable and fall back to self-reported check-ins only.

## Fairness audit

`evaluate.evaluate(model, loader, subgroups=…)` accepts a per-sample
subgroup label (any hashable) and returns per-subgroup screening F1. Wire
this up once you have demographic labels — at minimum split by region
(KPK / Punjab / Sindh / Balochistan / mixed) and by `isMinor` from
`GlobalAppState` since the Android app already tracks that flag.

## Limitations called out up front

* Stress is not annotated in any public FER dataset we use. Until a
  multimodal (face + physiological) dataset is wired in, the screening
  layer derives stress from `max(anger, disgust)` — a documented
  approximation, not a true stress classifier.
* The Haar-cascade face detector in `quality_gate.py` is a *coarse* check.
  Production deployments should upgrade to MediaPipe FaceMesh for landmark-
  based alignment verification.
* The screening output is a probability distribution and a level —
  **never** a diagnosis. Always pair with human counselor review on any
  ELEVATED or HIGH result.
