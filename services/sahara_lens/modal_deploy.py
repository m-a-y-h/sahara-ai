"""Sahara Lens screening service on Modal — pretrained-model backend.

Serves the existing FastAPI app (``sahara_lens.api``) with the model stage
swapped for a ready-made, Apache-2.0 facial-emotion ViT
(``dima806/facial_emotions_image_detection`` — ~91.9% accuracy, full 7 classes
including anger/disgust/fear). No training, no checkpoint upload, scale-to-zero.

The quality gate, screening layer, /scan response shape, and the Android client
are all unchanged — only the model that turns pixels into emotion probabilities
differs (selected via SAHARA_LENS_BACKEND=hf).

Deploy (run from the ``services/`` directory so the sahara_lens package resolves):
    modal deploy sahara_lens/modal_deploy.py

Modal prints an https URL. Set it in the app's local.properties:
    sahara.lens.scan.url=<that https URL>/v1/lens/scan
Health check:
    curl <that https URL>/v1/lens/healthz
"""

import modal

APP_NAME = "sahara-lens"
HF_MODEL = "dima806/facial_emotions_image_detection"
HF_HOME = "/models"


def _bake_model() -> None:
    # Runs at image-build time so the ~330 MB of weights are baked into the
    # image and cold starts don't re-download them.
    from transformers import AutoImageProcessor, AutoModelForImageClassification

    AutoImageProcessor.from_pretrained(HF_MODEL)
    AutoModelForImageClassification.from_pretrained(HF_MODEL)


image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("libglib2.0-0")  # runtime dep for opencv-python-headless
    # CPU torch keeps the image lean and matches the (GPU-less) function below.
    .pip_install(
        "torch==2.4.1",
        "torchvision==0.19.1",
        extra_index_url="https://download.pytorch.org/whl/cpu",
    )
    .pip_install(
        "transformers>=4.44,<5",
        "pillow>=10",
        "numpy<2",
        "opencv-python-headless>=4.9",
        "fastapi==0.115.0",
        "pydantic>=2.6,<3",
        "python-multipart>=0.0.9",
    )
    .env(
        {
            "SAHARA_LENS_BACKEND": "hf",
            "SAHARA_LENS_HF_MODEL": HF_MODEL,
            "HF_HOME": HF_HOME,
        }
    )
    .run_function(_bake_model)
    # Ship the local package into the image. Requires a current Modal release
    # (add_local_dir). On older Modal, drop this line and instead pass
    # mounts=[modal.Mount.from_local_dir("sahara_lens", remote_path="/root/sahara_lens")]
    # to @app.function below.
    .add_local_dir("sahara_lens", remote_path="/root/sahara_lens")
)

app = modal.App(name=APP_NAME, image=image)


@app.function(scaledown_window=300, timeout=120, memory=4096)
@modal.asgi_app(label="sahara-lens")
def fastapi_app():
    # build_standalone_app() mounts the router at /v1/lens, so the scan endpoint
    # is /v1/lens/scan and the liveness probe is /v1/lens/healthz.
    from sahara_lens.api import app as lens_app

    return lens_app
