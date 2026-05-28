"""Tests for the Sahara Lens FER pipeline.

The tests are split into three tiers by import requirements so they run in
any environment:

    Tier 1 — pure Python stdlib only:
        screening logic, config invariants

    Tier 2 — needs numpy + Pillow:
        quality gate (face detection skipped if opencv-python is missing)

    Tier 3 — needs torch + torchvision:
        model forward shape, partial-fine-tune parameter group construction

Run all available tiers:

    python -m unittest tests.test_sahara_lens
"""

from __future__ import annotations

import io
import unittest






def _has_numpy_and_pillow() -> bool:
    try:
        import numpy  
        from PIL import Image  
        return True
    except ImportError:
        return False


def _has_torch() -> bool:
    try:
        import torch  
        import torchvision  
        return True
    except ImportError:
        return False







class TestConfigInvariants(unittest.TestCase):
    """Things that must never silently change between commits."""

    def test_emotion_classes_are_canonical_and_unique(self):
        from sahara_lens.config import EMOTION_CLASSES

        self.assertEqual(len(EMOTION_CLASSES), len(set(EMOTION_CLASSES)))
        
        
        for required in ("fear", "sadness", "neutral", "anger", "disgust", "happiness", "surprise"):
            self.assertIn(required, EMOTION_CLASSES)

    def test_screening_classes_are_canonical(self):
        from sahara_lens.config import SCREENING_CLASSES, NEG_EMOTION_GROUP

        self.assertEqual(SCREENING_CLASSES, ("neutral", "stress", "sadness", "fear"))
        self.assertEqual(set(NEG_EMOTION_GROUP), {"stress", "sadness", "fear"})

    def test_class_index_lookup(self):
        from sahara_lens.config import class_index, EMOTION_CLASSES

        for i, name in enumerate(EMOTION_CLASSES):
            self.assertEqual(class_index(name), i)

        with self.assertRaises(ValueError):
            class_index("euphoria")


class TestScreening(unittest.TestCase):
    """The pure-Python emotion → screening mapping."""

    def _uniform(self):
        from sahara_lens.config import EMOTION_CLASSES

        p = 1.0 / len(EMOTION_CLASSES)
        return {c: p for c in EMOTION_CLASSES}

    def test_emotion_to_screening_is_a_distribution(self):
        from sahara_lens.screening import emotion_to_screening

        screening = emotion_to_screening(self._uniform())
        self.assertAlmostEqual(sum(screening.values()), 1.0, places=6)
        for v in screening.values():
            self.assertGreaterEqual(v, 0.0)

    def test_clean_neutral_face_is_screened_neutral(self):
        from sahara_lens.screening import screen_emotions, ScreeningLevel

        probs = {
            "anger": 0.02,
            "disgust": 0.02,
            "fear": 0.02,
            "happiness": 0.10,
            "neutral": 0.78,
            "sadness": 0.03,
            "surprise": 0.03,
        }
        result = screen_emotions(probs)
        self.assertEqual(result.level, ScreeningLevel.NEUTRAL)
        self.assertEqual(result.top_screening_class, "neutral")

    def test_high_fear_is_routed_to_high(self):
        from sahara_lens.screening import screen_emotions, ScreeningLevel

        probs = {
            "anger": 0.05,
            "disgust": 0.05,
            "fear": 0.65,
            "happiness": 0.02,
            "neutral": 0.05,
            "sadness": 0.13,
            "surprise": 0.05,
        }
        result = screen_emotions(probs)
        self.assertIn(result.level, (ScreeningLevel.ELEVATED, ScreeningLevel.HIGH))
        
        self.assertEqual(result.level, ScreeningLevel.HIGH)
        self.assertGreater(result.screening_probs["fear"], result.screening_probs["neutral"])

    def test_sadness_plus_anger_lifts_to_elevated(self):
        from sahara_lens.screening import screen_emotions, ScreeningLevel

        probs = {
            "anger": 0.30,        
            "disgust": 0.08,
            "fear": 0.05,
            "happiness": 0.05,
            "neutral": 0.20,
            "sadness": 0.27,
            "surprise": 0.05,
        }
        result = screen_emotions(probs)
        
        self.assertNotEqual(result.level, ScreeningLevel.NEUTRAL)
        self.assertNotEqual(result.level, ScreeningLevel.UNCERTAIN)

    def test_low_confidence_is_uncertain(self):
        from sahara_lens.screening import screen_emotions, ScreeningLevel

        
        
        
        probs = {
            "anger": 0.30,
            "disgust": 0.10,
            "fear": 0.20,
            "happiness": 0.05,
            "neutral": 0.05,
            "sadness": 0.20,
            "surprise": 0.10,
        }
        result = screen_emotions(probs)
        self.assertEqual(result.level, ScreeningLevel.UNCERTAIN)
        self.assertLess(result.top_screening_prob, 0.35)

    def test_rejects_missing_classes(self):
        from sahara_lens.screening import screen_emotions

        with self.assertRaises(ValueError):
            screen_emotions({"happiness": 1.0})

    def test_accepts_ordered_sequence(self):
        from sahara_lens.screening import screen_emotions
        from sahara_lens.config import EMOTION_CLASSES

        seq = [1.0 / len(EMOTION_CLASSES)] * len(EMOTION_CLASSES)
        result = screen_emotions(seq)
        
        self.assertAlmostEqual(sum(result.screening_probs.values()), 1.0, places=6)







@unittest.skipUnless(_has_numpy_and_pillow(), "numpy and Pillow not installed")
class TestQualityGate(unittest.TestCase):
    """Quality gate behaviour. Face detection is skipped if opencv missing."""

    def _make_image_bytes(self, w: int, h: int, fill=(128, 128, 128), pattern: str = "solid") -> bytes:
        from PIL import Image
        import numpy as np

        if pattern == "solid":
            arr = np.full((h, w, 3), fill, dtype=np.uint8)
        elif pattern == "noise":
            arr = np.random.randint(0, 256, (h, w, 3), dtype=np.uint8)
        elif pattern == "stripes":
            arr = np.zeros((h, w, 3), dtype=np.uint8)
            for y in range(h):
                arr[y, :, :] = ((y * 8) % 256)
        else:
            raise ValueError(pattern)
        buf = io.BytesIO()
        Image.fromarray(arr).save(buf, format="JPEG", quality=90)
        return buf.getvalue()

    def test_rejects_undecodable_image(self):
        from sahara_lens.quality_gate import run_quality_gate

        result = run_quality_gate(b"this is not an image")
        self.assertFalse(result.passed)
        self.assertTrue(any("decode" in r for r in result.reasons))

    def test_rejects_too_small_image(self):
        from sahara_lens.quality_gate import run_quality_gate

        data = self._make_image_bytes(50, 50, pattern="noise")
        result = run_quality_gate(data)
        self.assertFalse(result.passed)
        self.assertTrue(any("resolution" in r for r in result.reasons))

    def test_rejects_solid_dark_image(self):
        from sahara_lens.quality_gate import run_quality_gate

        data = self._make_image_bytes(300, 300, fill=(10, 10, 10), pattern="solid")
        result = run_quality_gate(data)
        self.assertFalse(result.passed)
        
        self.assertTrue(any(k in r for r in result.reasons for k in ("dark", "flat")))

    def test_rejects_solid_overexposed_image(self):
        from sahara_lens.quality_gate import run_quality_gate

        data = self._make_image_bytes(300, 300, fill=(245, 245, 245), pattern="solid")
        result = run_quality_gate(data)
        self.assertFalse(result.passed)
        
        self.assertTrue(any(k in r for k in ("bright", "flat") for r in result.reasons))

    def test_noisy_image_passes_or_flags_no_face(self):
        """Random-noise images either pass (no face detector available) or fail
        at the face-detection step. They should never fail earlier checks."""
        from sahara_lens.quality_gate import run_quality_gate

        data = self._make_image_bytes(400, 400, pattern="noise")
        result = run_quality_gate(data)
        if result.passed:
            
            self.assertTrue(any("face detector unavailable" in r for r in result.reasons))
        else:
            
            self.assertTrue(any("face" in r for r in result.reasons))







@unittest.skipUnless(_has_torch(), "torch and torchvision not installed")
class TestModelArchitecture(unittest.TestCase):
    """Forward-shape sanity checks. We skip pretrained weight download in tests."""

    def test_forward_output_shape(self):
        import torch

        from sahara_lens.config import EMOTION_CLASSES
        from sahara_lens.model import HybridResNetViT

        model = HybridResNetViT(num_classes=len(EMOTION_CLASSES), pretrained=False)
        model.eval()
        x = torch.zeros(2, 3, 224, 224)
        with torch.no_grad():
            y = model(x)
        self.assertEqual(y.shape, (2, len(EMOTION_CLASSES)))

    def test_predict_probs_normalises(self):
        import torch

        from sahara_lens.model import HybridResNetViT
        from sahara_lens.config import EMOTION_CLASSES

        model = HybridResNetViT(num_classes=len(EMOTION_CLASSES), pretrained=False)
        x = torch.zeros(1, 3, 224, 224)
        probs = model.predict_probs(x)
        self.assertEqual(probs.shape, (1, len(EMOTION_CLASSES)))
        self.assertAlmostEqual(float(probs.sum().item()), 1.0, places=4)

    def test_partial_finetune_freezes_stem(self):
        from sahara_lens.config import ModelConfig
        from sahara_lens.model import HybridResNetViT

        cfg = ModelConfig(partial_finetune=True)
        model = HybridResNetViT(config=cfg, pretrained=False)
        
        for p in model.cnn_stem.parameters():
            self.assertFalse(p.requires_grad)
        
        for p in model.cnn_top.parameters():
            self.assertTrue(p.requires_grad)
        for p in model.blocks.parameters():
            self.assertTrue(p.requires_grad)

    def test_parameter_groups_split_lrs(self):
        from sahara_lens.model import HybridResNetViT

        model = HybridResNetViT(pretrained=False)
        groups = model.parameter_groups(lr_backbone=1e-5, lr_head=1e-4)
        self.assertEqual(len(groups), 2)
        names = {g["name"] for g in groups}
        self.assertEqual(names, {"backbone", "head"})
        for g in groups:
            if g["name"] == "backbone":
                self.assertAlmostEqual(g["lr"], 1e-5)
            else:
                self.assertAlmostEqual(g["lr"], 1e-4)


if __name__ == "__main__":
    unittest.main()
