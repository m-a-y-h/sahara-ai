"""Speech-backbone emotion classifier for the team's checkpoint architecture.

Differences from the teammate's snippet:

    * The speech backbone is configurable via ``ModelConfig`` and loaded with
      ``AutoModel``. Production can use XLS-R, HuBERT, Wav2Vec2, or another
      encoder with the same waveform -> ``last_hidden_state`` interface.
    * BatchNorm is replaced with LayerNorm in the projection head so a single
      inference call (batch size 1, which is what the API does) doesn't
      crash with "Expected more than 1 value per channel".
    * Forward returns logits; softmax happens in the inference engine so
      downstream code can compute losses without redoing the softmax.

Architecture:

    input (B, T) waveform at 16 kHz
      → speech encoder                → (B, T', D)
      → attention pooling             → (B, D)
      → LayerNorm + dropout + GELU MLP → (B, num_classes) logits
"""

from __future__ import annotations

from dataclasses import replace
from typing import Optional

import torch
import torch.nn as nn
import torch.nn.functional as F

from .config import VoiceModelConfig, DEFAULT_MODEL_CONFIG


class HubertEmotionClassifier(nn.Module):
    """Speech encoder + attention pooling + MLP head.

    The class name is kept for checkpoint compatibility with older training
    scripts that saved parameters under ``hubert.*`` keys.
    """

    def __init__(
        self,
        config: Optional[VoiceModelConfig] = None,
        *,
        num_classes: Optional[int] = None,
        pretrained: bool = True,
    ) -> None:
        super().__init__()

        # AutoModel (not HubertModel) so any speech encoder with the same
        # `(B, T)` waveform -> `last_hidden_state` interface loads here —
        # HuBERT base/large for the original team checkpoint, and Wav2Vec2 /
        # XLS-R for the multilingual fine-tune we now use on Urdu. The
        # attribute is still named `self.hubert` so state-dicts saved against
        # the old class layout keep loading without rename.
        from transformers import AutoModel

        self.config = config or DEFAULT_MODEL_CONFIG
        nc = num_classes if num_classes is not None else self.config.num_classes
        self.num_classes = nc

        if pretrained:
            self.hubert = AutoModel.from_pretrained(self.config.backbone)
        else:
            from transformers import AutoConfig
            self.hubert = AutoModel.from_config(AutoConfig.from_pretrained(self.config.backbone))

        d = self.hubert.config.hidden_size

        
        self.attn_pool = nn.Linear(d, 1)
        self.norm = nn.LayerNorm(d)

        self.drop_in = nn.Dropout(self.config.dropout_in)
        self.fc1 = nn.Linear(d, self.config.hidden1)
        
        self.ln1 = nn.LayerNorm(self.config.hidden1)
        self.act = nn.GELU()

        self.drop_mid = nn.Dropout(self.config.dropout_mid)
        self.fc2 = nn.Linear(self.config.hidden1, self.config.hidden2)

        self.drop_out = nn.Dropout(self.config.dropout_out)
        self.out = nn.Linear(self.config.hidden2, nc)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Forward pass. ``x`` is a (B, T) float waveform at 16 kHz."""
        hidden = self.hubert(x).last_hidden_state              
        attn_weights = torch.softmax(self.attn_pool(hidden), dim=1)
        ctx = (hidden * attn_weights).sum(dim=1)               

        h = self.drop_in(self.norm(ctx))
        h = self.act(self.ln1(self.fc1(h)))
        h = self.drop_mid(h)
        h = self.act(self.fc2(h))
        h = self.drop_out(h)
        return self.out(h)

    @torch.no_grad()
    def predict_probs(self, x: torch.Tensor) -> torch.Tensor:
        """Softmax-normalised class probabilities (B, num_classes)."""
        self.eval()
        return F.softmax(self.forward(x), dim=-1)


def load_classifier_from_checkpoint(
    checkpoint_path: str,
    config: Optional[VoiceModelConfig] = None,
    *,
    num_classes: Optional[int] = None,
    map_location: str | torch.device = "cpu",
) -> HubertEmotionClassifier:
    """Load a saved checkpoint produced by the teammate's training script.

    The checkpoint format is:

    ::

        {
            "model_state_dict": <state_dict>,
            "config": {...},      # optional
            ...
        }
    """
    state = torch.load(checkpoint_path, map_location=map_location)
    sd = state.get("model_state_dict", state)
    cfg = _config_with_checkpoint_head(config or DEFAULT_MODEL_CONFIG, sd, num_classes)
    effective_num_classes = num_classes if num_classes is not None else cfg.num_classes
    model = HubertEmotionClassifier(config=cfg, num_classes=effective_num_classes, pretrained=False)

    try:
        missing, unexpected = model.load_state_dict(sd, strict=False)
    except RuntimeError as exc:
        raise RuntimeError(
            "Voice checkpoint does not match VoiceModelConfig. "
            f"Checkpoint has {_describe_checkpoint_shapes(sd)}; "
            f"configured model has {_describe_model_shapes(model)}. "
            "Upload a matching model_config.json beside the checkpoint with the "
            "correct backbone and id2label. Classifier head dimensions are inferred "
            "from the checkpoint when omitted."
        ) from exc
    if missing or unexpected:
        import logging
        logging.getLogger("sahara_voice.model").warning(
            f"checkpoint partial-loaded: missing={len(missing)} unexpected={len(unexpected)}; "
            "this is expected when migrating a BatchNorm-head checkpoint to the LayerNorm head."
        )
    model.eval()
    return model


def _config_with_checkpoint_head(
    config: VoiceModelConfig,
    sd: dict[str, torch.Tensor],
    num_classes: Optional[int],
) -> VoiceModelConfig:
    hidden1, hidden2, checkpoint_classes = _infer_checkpoint_head(sd)
    updates: dict[str, int] = {}
    if hidden1 is not None and hidden1 != config.hidden1:
        updates["hidden1"] = hidden1
    if hidden2 is not None and hidden2 != config.hidden2:
        updates["hidden2"] = hidden2
    if num_classes is None and checkpoint_classes is not None and checkpoint_classes != config.num_classes:
        updates["num_classes"] = checkpoint_classes
    return replace(config, **updates) if updates else config


def _infer_checkpoint_head(
    sd: dict[str, torch.Tensor],
) -> tuple[Optional[int], Optional[int], Optional[int]]:
    fc1 = sd.get("fc1.weight")
    fc2 = sd.get("fc2.weight")
    out = sd.get("out.weight")
    hidden1 = int(fc1.shape[0]) if isinstance(fc1, torch.Tensor) and fc1.ndim == 2 else None
    hidden2 = int(fc2.shape[0]) if isinstance(fc2, torch.Tensor) and fc2.ndim == 2 else None
    classes = int(out.shape[0]) if isinstance(out, torch.Tensor) and out.ndim == 2 else None
    return hidden1, hidden2, classes


def _describe_checkpoint_shapes(sd: dict[str, torch.Tensor]) -> str:
    fc1 = sd.get("fc1.weight")
    fc2 = sd.get("fc2.weight")
    out = sd.get("out.weight")
    attn = sd.get("attn_pool.weight")
    hidden_size = None
    if isinstance(fc1, torch.Tensor) and fc1.ndim == 2:
        hidden_size = int(fc1.shape[1])
    elif isinstance(attn, torch.Tensor) and attn.ndim == 2:
        hidden_size = int(attn.shape[1])

    parts: list[str] = []
    if hidden_size is not None:
        parts.append(f"backbone_hidden_size={hidden_size}")
    if isinstance(fc1, torch.Tensor) and fc1.ndim == 2:
        parts.append(f"hidden1={int(fc1.shape[0])}")
    if isinstance(fc2, torch.Tensor) and fc2.ndim == 2:
        parts.append(f"hidden2={int(fc2.shape[0])}")
    if isinstance(out, torch.Tensor) and out.ndim == 2:
        parts.append(f"num_classes={int(out.shape[0])}")
    return ", ".join(parts) if parts else "unknown classifier shapes"


def _describe_model_shapes(model: HubertEmotionClassifier) -> str:
    return (
        f"backbone={model.config.backbone!r}, "
        f"backbone_hidden_size={model.hubert.config.hidden_size}, "
        f"hidden1={model.config.hidden1}, "
        f"hidden2={model.config.hidden2}, "
        f"num_classes={model.num_classes}"
    )
