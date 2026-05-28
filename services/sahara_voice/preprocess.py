"""Audio I/O + preprocessing pipeline used by the inference engine.

Pipeline:

    bytes → decode to mono float32 → denoise → resample to 16 kHz →
        peak-normalise → trim leading/trailing silence → pad/cut to 6 s.

The pipeline is the same as the teammate's snippet, with two production-friendly
additions:

    * The decoder accepts raw bytes via ``soundfile`` so the FastAPI route can
      hand the request body straight in — no temp-file dance.
    * A short-audio guard ensures the resulting tensor is always at least
      0.5 s long. HuBERT's convolutional stem otherwise emits an empty
      sequence and the attention pooling layer collapses.
"""

from __future__ import annotations

import io
import logging
from typing import Optional

import numpy as np

from .config import MAX_LENGTH_SAMPLES, SAMPLE_RATE
from .noise import denoise

logger = logging.getLogger("sahara_voice.preprocess")

_MIN_LENGTH_SAMPLES = SAMPLE_RATE // 2   


def _decode_audio_bytes(data: bytes) -> tuple[np.ndarray, int]:
    """Decode WAV/FLAC/OGG/MP3 bytes to (mono float32 PCM, sample_rate)."""
    try:
        import soundfile as sf  
        with sf.SoundFile(io.BytesIO(data)) as f:
            audio = f.read(dtype="float32", always_2d=False)
            sr = f.samplerate
        if audio.ndim > 1:
            audio = audio.mean(axis=1)
        return audio.astype(np.float32, copy=False), int(sr)
    except Exception:
        
        
        try:
            import librosa  
        except ImportError as exc:
            raise RuntimeError(
                "Neither soundfile nor librosa is installed; cannot decode audio."
            ) from exc
        audio, sr = librosa.load(io.BytesIO(data), sr=None, mono=True)
        return audio.astype(np.float32, copy=False), int(sr)


def _resample(audio: np.ndarray, src_sr: int, dst_sr: int) -> np.ndarray:
    if src_sr == dst_sr:
        return audio
    try:
        import librosa  
        return librosa.resample(audio, orig_sr=src_sr, target_sr=dst_sr).astype(np.float32)
    except ImportError:
        
        
        
        if audio.size == 0:
            return audio
        old_idx = np.linspace(0.0, 1.0, num=audio.size, endpoint=False)
        new_n = max(1, int(round(audio.size * dst_sr / src_sr)))
        new_idx = np.linspace(0.0, 1.0, num=new_n, endpoint=False)
        return np.interp(new_idx, old_idx, audio).astype(np.float32)


def _trim_silence(audio: np.ndarray, sr: int, top_db: float = 30.0) -> np.ndarray:
    if audio.size == 0:
        return audio
    try:
        import librosa  
        trimmed, _ = librosa.effects.trim(audio, top_db=top_db)
        return trimmed.astype(np.float32) if trimmed.size > 0 else audio
    except ImportError:
        
        
        
        if audio.size == 0:
            return audio
        peak = float(np.max(np.abs(audio)))
        if peak <= 0:
            return audio
        threshold = peak * (10.0 ** (-top_db / 20.0))
        mask = np.abs(audio) > threshold
        if not mask.any():
            return audio
        first, last = int(np.argmax(mask)), audio.size - int(np.argmax(mask[::-1]))
        return audio[first:last]


def _pad_or_cut(audio: np.ndarray, target: int) -> np.ndarray:
    if audio.size >= target:
        return audio[:target]
    return np.pad(audio, (0, target - audio.size))


def preprocess_audio_bytes(
    data: bytes,
    *,
    target_sr: int = SAMPLE_RATE,
    target_length: int = MAX_LENGTH_SAMPLES,
    apply_denoise: bool = True,
) -> np.ndarray:
    """End-to-end preprocessing.

    Returns a float32 mono waveform exactly ``target_length`` samples long at
    ``target_sr``. Safe to pass straight to ``Wav2Vec2FeatureExtractor`` or to
    ``HubertModel(input_values)``.
    """
    audio, sr = _decode_audio_bytes(data)
    if audio.size == 0:
        raise ValueError("decoded audio is empty")

    if apply_denoise:
        audio = denoise(audio, sr)

    if sr != target_sr:
        audio = _resample(audio, sr, target_sr)

    peak = float(np.max(np.abs(audio)))
    if peak > 0:
        audio = audio / peak

    audio = _trim_silence(audio, target_sr)
    if audio.size < _MIN_LENGTH_SAMPLES:
        
        
        
        audio = np.pad(audio, (_MIN_LENGTH_SAMPLES - audio.size, 0))

    return _pad_or_cut(audio, target_length).astype(np.float32, copy=False)
