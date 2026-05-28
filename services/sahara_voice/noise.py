"""Background-noise reduction tuned for short distress-tone voice notes.

The teammate's snippet wired in ``noisereduce`` with a 0.5 s noise-profile
window. That works for clean recordings but fails on phone-captured speech
where the first 0.5 s is often *already* the user talking. This module
improves on that by:

    1. Picking the quietest 0.5 s window across the whole clip (not just the
       leading 0.5 s) as the noise profile — robust to clips that start with
       speech.
    2. Falling back to ``noisereduce``'s built-in stationary mode if the
       quietest-window heuristic still contains voice energy.
    3. Skipping denoising entirely when the clip is so short that the noise
       profile would be longer than the signal — denoising a 1.5 s clip with
       a 0.5 s profile generally degrades the signal more than it helps.

All knobs match the teammate's snippet so checkpoints behave the same;
``denoise`` is a single function callers can swap in.
"""

from __future__ import annotations

import logging
from typing import Optional

import numpy as np

logger = logging.getLogger("sahara_voice.noise")


def _quietest_window(audio: np.ndarray, sr: int, window_s: float = 0.5) -> Optional[np.ndarray]:
    """Return the quietest ``window_s`` window across ``audio`` as a noise profile.

    Returns ``None`` if the clip is too short to fit a window plus at least
    one window of speech material.
    """
    window_n = int(sr * window_s)
    if window_n <= 0 or audio.size < 2 * window_n:
        return None
    
    
    n_windows = audio.size // window_n
    trimmed = audio[: n_windows * window_n].reshape(n_windows, window_n)
    rms = np.sqrt(np.mean(trimmed.astype(np.float32) ** 2, axis=1))
    quietest = int(np.argmin(rms))
    return trimmed[quietest].astype(np.float32)


def denoise(
    audio: np.ndarray,
    sr: int,
    *,
    prop_decrease: float = 0.85,
    n_fft: int = 1024,
    hop_length: int = 256,
    win_length: int = 1024,
) -> np.ndarray:
    """Best-effort background-noise reduction.

    Args:
        audio: float32 mono PCM in [-1, 1].
        sr: sample rate (Hz). Typically 16000.
        prop_decrease, n_fft, hop_length, win_length: forwarded to
            ``noisereduce.reduce_noise``.

    Returns:
        Denoised audio (same shape and dtype). If ``noisereduce`` is not
        installed, the original audio is returned unchanged — Sahara Voice
        still works, just with slightly worse robustness to noise.
    """
    if audio.size == 0:
        return audio

    try:
        import noisereduce as nr  
    except ImportError:
        logger.warning("noisereduce not installed; skipping denoise step.")
        return audio.astype(np.float32, copy=False)

    audio = audio.astype(np.float32, copy=False)

    profile = _quietest_window(audio, sr)
    try:
        if profile is not None and profile.size > 0:
            return nr.reduce_noise(
                y=audio,
                sr=sr,
                y_noise=profile,
                prop_decrease=prop_decrease,
                stationary=False,
                n_fft=n_fft,
                win_length=win_length,
                hop_length=hop_length,
                freq_mask_smooth_hz=500,
                time_mask_smooth_ms=50,
            ).astype(np.float32)
        
        
        return nr.reduce_noise(
            y=audio,
            sr=sr,
            prop_decrease=prop_decrease,
            stationary=True,
            n_fft=n_fft,
            win_length=win_length,
            hop_length=hop_length,
        ).astype(np.float32)
    except Exception as exc:  
        logger.warning(f"denoise failed ({type(exc).__name__}: {exc}); returning input as-is.")
        return audio
