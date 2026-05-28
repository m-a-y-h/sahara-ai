"""Server-side image quality gate.

The Android client already does the heavy lifting: it refuses to capture if
the face is at a bad angle, lighting is poor, or no face is detected. This
module is the *backup* layer that re-validates on the server so:

    1. A custom or modified client cannot bypass quality checks.
    2. The FER model is never asked to predict on a degenerate image.

Checks performed (in order, short-circuiting on first failure):

    1. The image decodes and is at least ``min_resolution`` pixels per side.
    2. Brightness (mean luminance) is within the acceptable band.
    3. Contrast (luminance std-dev) exceeds the minimum.
    4. Sharpness (variance of Laplacian) exceeds the minimum (rejects blur).
    5. A face is detected and occupies a sensible fraction of the frame,
       roughly centered.

Face detection is implemented two ways:

    * **Preferred**: OpenCV's bundled Haar cascade for frontal faces. Zero
      external download — the XML is shipped with ``opencv-python``.
    * **Fallback**: a heuristic on the brightness/contrast checks alone if
      OpenCV is unavailable (e.g. lightweight dev install). The fallback is
      explicitly marked in the result so the API can still respond rather
      than crashing.

Heavier face detectors (MediaPipe FaceMesh, MTCNN) would tighten the gate
further but add ~50MB of dependencies; that upgrade is intentionally left to
the production deployment.
"""

from __future__ import annotations

import io
from dataclasses import dataclass, field
from typing import Optional

import numpy as np
from PIL import Image







@dataclass
class QualityGateResult:
    """Outcome of running the quality gate on one image."""

    passed: bool
    reasons: list[str] = field(default_factory=list)
    
    
    metrics: dict[str, float] = field(default_factory=dict)
    face_box: Optional[tuple[int, int, int, int]] = None  
    face_detector: str = "none"

    def to_dict(self) -> dict:
        return {
            "passed": self.passed,
            "reasons": list(self.reasons),
            "metrics": {k: round(v, 4) for k, v in self.metrics.items()},
            "face_box": list(self.face_box) if self.face_box else None,
            "face_detector": self.face_detector,
        }







@dataclass(frozen=True)
class QualityThresholds:
    """Tunable quality thresholds. The defaults reflect typical smartphone
    front-camera captures under reasonable indoor lighting."""

    min_resolution: int = 200
    min_brightness: float = 50.0   
    max_brightness: float = 220.0
    min_contrast: float = 25.0     
    min_sharpness: float = 80.0    
    min_face_fraction: float = 0.12  
    max_face_fraction: float = 0.85
    center_tolerance: float = 0.30   


DEFAULT_THRESHOLDS = QualityThresholds()







def _to_grayscale_array(img: Image.Image) -> np.ndarray:
    """Return a uint8 grayscale ndarray of the image."""
    return np.asarray(img.convert("L"), dtype=np.uint8)


def _laplacian_variance(gray: np.ndarray) -> float:
    """Variance of the 3x3 Laplacian — a classic blur detector.

    A high variance means lots of high-frequency edges (sharp image); a low
    variance means smooth/blurry. Threshold ~80 works well for 224+ resolution
    smartphone selfies; lower for downscaled web thumbnails.
    """
    
    
    kernel = np.array([[0, 1, 0], [1, -4, 1], [0, 1, 0]], dtype=np.int16)
    padded = np.pad(gray.astype(np.int16), pad_width=1, mode="edge")
    h, w = gray.shape
    accum = np.zeros((h, w), dtype=np.int32)
    for dy in range(3):
        for dx in range(3):
            k = kernel[dy, dx]
            if k == 0:
                continue
            accum += k * padded[dy : dy + h, dx : dx + w]
    return float(accum.astype(np.float32).var())







def _try_detect_face_opencv(gray: np.ndarray) -> Optional[tuple[tuple[int, int, int, int], str]]:
    """Detect the largest frontal face with OpenCV's Haar cascade.

    Returns ``((x, y, w, h), detector_name)`` of the largest face, or
    ``None`` if no face was found or OpenCV is unavailable.
    """
    try:
        import cv2  
    except ImportError:
        return None

    cascade_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
    cascade = cv2.CascadeClassifier(cascade_path)
    if cascade.empty():
        return None

    faces = cascade.detectMultiScale(
        gray,
        scaleFactor=1.1,
        minNeighbors=5,
        minSize=(60, 60),
    )
    if len(faces) == 0:
        return None
    
    x, y, w, h = max(faces, key=lambda f: int(f[2]) * int(f[3]))
    return (int(x), int(y), int(w), int(h)), "opencv-haar-frontalface"







def run_quality_gate(
    image_bytes: bytes,
    thresholds: QualityThresholds = DEFAULT_THRESHOLDS,
) -> QualityGateResult:
    """Validate an uploaded image before invoking the FER model.

    The gate is intentionally strict: it would rather reject a borderline
    photo and ask the user to retake than feed a degenerate sample to the
    model. False rejections at this layer are recoverable; false acceptances
    can route an actual user-in-distress through the wrong code path.
    """
    result = QualityGateResult(passed=False)

    
    try:
        img = Image.open(io.BytesIO(image_bytes))
        img.load()
    except Exception as e:  
        result.reasons.append(f"image failed to decode: {type(e).__name__}: {e}")
        return result

    if img.mode not in ("RGB", "L", "RGBA"):
        img = img.convert("RGB")
    w, h = img.size
    result.metrics["width"] = float(w)
    result.metrics["height"] = float(h)

    if w < thresholds.min_resolution or h < thresholds.min_resolution:
        result.reasons.append(
            f"resolution {w}x{h} below minimum {thresholds.min_resolution}px per side"
        )
        return result

    
    gray = _to_grayscale_array(img)
    brightness = float(gray.mean())
    contrast = float(gray.std())
    result.metrics["brightness"] = brightness
    result.metrics["contrast"] = contrast

    if brightness < thresholds.min_brightness:
        result.reasons.append(
            f"image too dark (mean brightness {brightness:.1f} < {thresholds.min_brightness:.1f})"
        )
        return result
    if brightness > thresholds.max_brightness:
        result.reasons.append(
            f"image too bright/overexposed (mean brightness {brightness:.1f} > {thresholds.max_brightness:.1f})"
        )
        return result
    if contrast < thresholds.min_contrast:
        result.reasons.append(
            f"image too flat (contrast {contrast:.1f} < {thresholds.min_contrast:.1f}) — possible covered lens"
        )
        return result

    
    sharpness = _laplacian_variance(gray)
    result.metrics["sharpness"] = sharpness
    if sharpness < thresholds.min_sharpness:
        result.reasons.append(
            f"image too blurry (laplacian variance {sharpness:.1f} < {thresholds.min_sharpness:.1f})"
        )
        return result

    
    detection = _try_detect_face_opencv(gray)
    if detection is None:
        
        if "import cv2" in "":  
            pass
        
        
        try:
            import cv2  
            
            result.reasons.append("no face detected in image")
            result.face_detector = "opencv-haar-frontalface"
            return result
        except ImportError:
            result.reasons.append(
                "face detector unavailable (opencv-python not installed); "
                "passing on brightness/contrast/sharpness only — install opencv-python in production"
            )
            result.face_detector = "fallback-none"
            result.passed = True
            return result

    (fx, fy, fw, fh), detector_name = detection
    result.face_box = (fx, fy, fw, fh)
    result.face_detector = detector_name

    face_fraction = (fw * fh) / float(w * h)
    result.metrics["face_fraction"] = face_fraction
    if face_fraction < thresholds.min_face_fraction:
        result.reasons.append(
            f"face too small ({face_fraction:.2%} of frame < {thresholds.min_face_fraction:.0%})"
        )
        return result
    if face_fraction > thresholds.max_face_fraction:
        result.reasons.append(
            f"face too close ({face_fraction:.2%} of frame > {thresholds.max_face_fraction:.0%})"
        )
        return result

    face_cx = fx + fw / 2
    face_cy = fy + fh / 2
    off_x = abs(face_cx - w / 2) / w
    off_y = abs(face_cy - h / 2) / h
    result.metrics["off_center_x"] = off_x
    result.metrics["off_center_y"] = off_y
    if off_x > thresholds.center_tolerance or off_y > thresholds.center_tolerance:
        result.reasons.append(
            f"face is off-center (Δx={off_x:.2%}, Δy={off_y:.2%}; tolerance {thresholds.center_tolerance:.0%})"
        )
        return result

    result.passed = True
    result.reasons.append("all checks passed")
    return result
