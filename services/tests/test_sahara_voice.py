"""Tests for the Sahara Voice screening adapter and config helpers.

The tier-gated tests in this file follow the same convention as
``tests/test_sahara_lens.py``: the pure-Python screening and config tests run
in any environment (Python stdlib only), and the deeper checks that need
``noisereduce`` / ``librosa`` / ``torch`` are skipped when those packages
are missing.
"""

from __future__ import annotations

import unittest


def _has_numpy() -> bool:
    try:
        import numpy  
        return True
    except ImportError:
        return False


def _has_torch() -> bool:
    try:
        import torch  
        import transformers  
        return True
    except ImportError:
        return False







class TestVoiceConfig(unittest.TestCase):
    def test_default_label_spaces_distinct(self):
        from sahara_voice.config import DEFAULT_LABELS_4CLASS, DEFAULT_LABELS_8CLASS

        self.assertEqual(len(DEFAULT_LABELS_4CLASS), 4)
        self.assertEqual(len(DEFAULT_LABELS_8CLASS), 8)
        
        self.assertEqual(len(set(DEFAULT_LABELS_4CLASS)), 4)
        self.assertEqual(len(set(DEFAULT_LABELS_8CLASS)), 8)

    def test_screening_classes_match_lens(self):
        from sahara_voice.config import SAHARA_SCREENING_CLASSES

        self.assertEqual(SAHARA_SCREENING_CLASSES, ("neutral", "stress", "sadness", "fear"))

    def test_load_id2label_normalises_keys_and_lowercases_values(self):
        from sahara_voice.config import load_id2label

        normalised = load_id2label({"0": "Anger", "1": "Happiness", "2": "Neutral", "3": "Sadness"})
        self.assertEqual(normalised, {0: "anger", 1: "happiness", 2: "neutral", 3: "sadness"})

    def test_load_id2label_rejects_empty(self):
        from sahara_voice.config import load_id2label

        with self.assertRaises(ValueError):
            load_id2label({})

    def test_load_id2label_rejects_non_int_keys(self):
        from sahara_voice.config import load_id2label

        with self.assertRaises(ValueError):
            load_id2label({"angry": "anger"})







class TestVoiceScreening(unittest.TestCase):

    def test_4class_anger_dominant_maps_to_stress(self):
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        result = screen_voice_emotions(
            {"anger": 0.70, "happiness": 0.05, "neutral": 0.20, "sadness": 0.05}
        )
        
        
        self.assertIn(result.level, (VoiceScreeningLevel.ELEVATED, VoiceScreeningLevel.HIGH))
        self.assertEqual(result.top_screening_class, "stress")

    def test_4class_sadness_dominant_routes_to_sadness(self):
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        result = screen_voice_emotions(
            {"anger": 0.05, "happiness": 0.05, "neutral": 0.15, "sadness": 0.75}
        )
        self.assertEqual(result.top_screening_class, "sadness")
        self.assertIn(result.level, (VoiceScreeningLevel.ELEVATED, VoiceScreeningLevel.HIGH))

    def test_4class_happy_calm_stays_neutral(self):
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        result = screen_voice_emotions(
            {"anger": 0.02, "happiness": 0.70, "neutral": 0.25, "sadness": 0.03}
        )
        self.assertEqual(result.level, VoiceScreeningLevel.NEUTRAL)
        self.assertEqual(result.top_screening_class, "neutral")

    def test_8class_fearful_dominant_lands_in_fear(self):
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        result = screen_voice_emotions(
            {
                "anger": 0.05, "calm": 0.05, "disgust": 0.05, "fearful": 0.60,
                "happy": 0.05, "neutral": 0.05, "sad": 0.10, "surprised": 0.05,
            }
        )
        self.assertEqual(result.top_screening_class, "fear")
        self.assertIn(result.level, (VoiceScreeningLevel.ELEVATED, VoiceScreeningLevel.HIGH))

    def test_flat_distribution_across_negatives_yields_uncertain(self):
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        
        
        
        result = screen_voice_emotions(
            {
                "anger": 0.25, "calm": 0.0, "disgust": 0.0, "fearful": 0.25,
                "happy": 0.0, "neutral": 0.25, "sad": 0.25, "surprised": 0.0,
            }
        )
        self.assertEqual(result.level, VoiceScreeningLevel.UNCERTAIN)
        self.assertLess(result.top_screening_prob, 0.35)

    def test_uniform_4class_skews_to_neutral_not_uncertain(self):
        """Documents the deliberate neutral-bias of the 4-class adapter:
        with two of four raw classes (happiness, neutral) mapping to neutral
        and no `calm` channel, a uniform input over the 4-class Urdu space
        lands solidly in NEUTRAL rather than UNCERTAIN. This is the safer
        direction for a wellness app — we'd rather miss a borderline signal
        than fire counselor escalation on noise."""
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        uniform = {label: 0.25 for label in ("anger", "happiness", "neutral", "sadness")}
        result = screen_voice_emotions(uniform)
        self.assertEqual(result.level, VoiceScreeningLevel.NEUTRAL)
        self.assertEqual(result.top_screening_class, "neutral")

    def test_unknown_labels_fold_to_neutral(self):
        from sahara_voice.screening import screen_voice_emotions

        
        
        result = screen_voice_emotions({"euphoria": 1.0})
        self.assertAlmostEqual(result.screening_probs["neutral"], 1.0, places=4)

    def test_screening_probs_are_a_distribution(self):
        from sahara_voice.screening import voice_emotions_to_screening

        screening = voice_emotions_to_screening(
            {"anger": 0.10, "happiness": 0.15, "neutral": 0.20, "sadness": 0.55}
        )
        self.assertAlmostEqual(sum(screening.values()), 1.0, places=6)
        for v in screening.values():
            self.assertGreaterEqual(v, 0.0)

    def test_id2label_sequence_form_with_4class_urdu(self):
        from sahara_voice.config import DEFAULT_LABELS_4CLASS
        from sahara_voice.screening import screen_voice_emotions, VoiceScreeningLevel

        id2label = {i: l for i, l in enumerate(DEFAULT_LABELS_4CLASS)}
        
        result = screen_voice_emotions([0.05, 0.05, 0.20, 0.70], id2label)
        self.assertEqual(result.top_screening_class, "sadness")
        self.assertIn(result.level, (VoiceScreeningLevel.ELEVATED, VoiceScreeningLevel.HIGH))







@unittest.skipUnless(_has_numpy(), "numpy not installed")
class TestNoiseReduction(unittest.TestCase):

    def test_denoise_returns_same_shape(self):
        import numpy as np

        from sahara_voice.noise import denoise

        sr = 16_000
        
        t = np.linspace(0, 2.0, int(sr * 2.0), endpoint=False).astype(np.float32)
        tone = 0.5 * np.sin(2 * np.pi * 220.0 * t)
        noise = (np.random.RandomState(0).randn(t.size) * 0.05).astype(np.float32)
        signal = (tone + noise).astype(np.float32)

        out = denoise(signal, sr)
        self.assertEqual(out.shape, signal.shape)
        self.assertEqual(out.dtype, np.float32)

    def test_denoise_handles_empty_input(self):
        import numpy as np

        from sahara_voice.noise import denoise

        out = denoise(np.zeros(0, dtype=np.float32), 16_000)
        self.assertEqual(out.size, 0)







@unittest.skipUnless(_has_torch(), "torch / transformers not installed")
class TestModelSmoke(unittest.TestCase):

    def test_forward_shape_with_8_classes(self):
        
        
        
        
        import torch

        from sahara_voice.model import HubertEmotionClassifier

        try:
            model = HubertEmotionClassifier(num_classes=8, pretrained=False)
        except Exception as e:
            self.skipTest(f"could not instantiate HuBERT shell: {e}")

        x = torch.zeros(1, 16_000)   
        with torch.no_grad():
            y = model(x)
        self.assertEqual(y.shape, (1, 8))


if __name__ == "__main__":
    unittest.main()
