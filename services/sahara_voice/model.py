"""HuBERT-based emotion classifier — production-ready version of the team's
checkpoint architecture.

Differences from the teammate's snippet:

    * The HuBERT backbone is configurable (base or large) via ``ModelConfig``.
      The upstream Urdu repo trained on ``facebook/hubert-large-ls960-ft``;
      the teammate's RAVDESS-derived checkpoint uses ``hubert-base-ls960``.
      Both load with the same head, just with different hidden sizes.
    * BatchNorm is replaced with LayerNorm in the projection head so a single
      inference call (batch size 1, which is what the API does) doesn't
      crash with "Expected more than 1 value per channel".
    * Forward returns logits; softmax happens in the inference engine so
      downstream code can compute losses without redoing the softmax.

Architecture:

    input (B, T) waveform at 16 kHz
      → HuBERT encoder                → (B, T', D)
      → attention pooling             → (B, D)
      → LayerNorm + dropout + GELU MLP → (B, num_classes) logits
"""

from __future__ import annotations

from typing import Optional

import torch
import torch.nn as nn
import torch.nn.functional as F

from .config import VoiceModelConfig, DEFAULT_MODEL_CONFIG


class HubertEmotionClassifier(nn.Module):
    """HuBERT + attention pooling + MLP head."""

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
    model = HubertEmotionClassifier(config=config, num_classes=num_classes, pretrained=False)
    state = torch.load(checkpoint_path, map_location=map_location)
    sd = state.get("model_state_dict", state)
    
    
    
    
    missing, unexpected = model.load_state_dict(sd, strict=False)
    if missing or unexpected:
        import logging
        logging.getLogger("sahara_voice.model").warning(
            f"checkpoint partial-loaded: missing={len(missing)} unexpected={len(unexpected)}; "
            "this is expected when migrating a BatchNorm-head checkpoint to the LayerNorm head."
        )
    model.eval()
    return model
