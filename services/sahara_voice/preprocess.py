"""Audio I/O + preprocessing pipeline used by the inference engine.

Pipeline:

    bytes → decode to mono float32 → denoise → resample to 16 kHz →
        peak-normalise → trim leading/trailing silence → pad/cut to 6 s.

The pipeline is the same as the teammate's snippet, with two production-friendly
additions:

    * The decoder accepts raw bytes via ``soundfile`` so the FastAPI route can
      hand the request body straight in — no temp-file dance.
    * A short-audio guard ensures the resulting tensor is always at least
      0.5 s long. Speech encoders with convolutional stems otherwise emit an empty
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
    """Decode audio bytes to (mono float32 PCM, sample_rate).

    libsndfile (``soundfile``) reads WAV/FLAC/OGG straight from memory — the
    fast path. Phone recorders, however, hand us compressed MPEG-4/AAC (Android
    ``MediaRecorder`` ``.m4a``), MP3 or 3GP/AMR, none of which libsndfile can
    decode; and librosa's audioread fallback refuses an in-memory ``BytesIO``
    (it needs a real path). So for anything soundfile rejects we transcode
    through ffmpeg, which ships in the serving image and handles every container
    the mobile client can emit.
    """
    try:
        import soundfile as sf
        with sf.SoundFile(io.BytesIO(data)) as f:
            audio = f.read(dtype="float32", always_2d=False)
            sr = f.samplerate
        if audio.ndim > 1:
            audio = audio.mean(axis=1)
        return audio.astype(np.float32, copy=False), int(sr)
    except Exception as sf_exc:
        try:
            return _decode_via_ffmpeg(data)
        except Exception as ff_exc:
            raise RuntimeError(
                f"could not decode audio: soundfile said {sf_exc!r}; "
                f"ffmpeg fallback said {ff_exc!r}"
            ) from ff_exc


def _decode_via_ffmpeg(data: bytes) -> tuple[np.ndarray, int]:
    """Transcode arbitrary container bytes to mono float32 PCM via ffmpeg.

    MP4/M4A needs a *seekable* input (the ``moov`` atom can sit at the end of
    the file), so the bytes go to a temp file rather than ffmpeg's stdin pipe.
    ffmpeg decodes straight to 32-bit float, mono, at the model's sample rate,
    which we read into numpy with no second decode hop — so the returned sr is
    always ``SAMPLE_RATE`` and the downstream resample is a no-op.
    """
    import os
    import shutil
    import subprocess
    import tempfile

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise RuntimeError("ffmpeg not found on PATH; cannot decode compressed audio")

    tmp_path: Optional[str] = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".audio", delete=False) as tmp:
            tmp.write(data)
            tmp_path = tmp.name
        proc = subprocess.run(
            [
                ffmpeg, "-nostdin", "-loglevel", "error",
                "-i", tmp_path,
                "-f", "f32le", "-ac", "1", "-ar", str(SAMPLE_RATE), "pipe:1",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.decode("utf-8", "ignore").strip()[-300:]
        raise RuntimeError(f"ffmpeg failed to decode audio: {detail}") from exc
    finally:
        if tmp_path is not None:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass

    # frombuffer is read-only and backed by ffmpeg's bytes; copy so the rest of
    # the pipeline (denoise, in-place normalise) can write to it.
    audio = np.frombuffer(proc.stdout, dtype="<f4").astype(np.float32)
    return audio, SAMPLE_RATE


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
