"""Hybrid ResNet-50 + Vision Transformer architecture for FER.

Design rationale (from the South Asian FER research synthesis bundled in the
project wiki): a strong CNN trunk extracts robust local AU-style features and
a small ViT head re-attends over them globally. This combination has been
shown to reach >95% testing accuracy on FER benchmarks while remaining
trainable on a single GPU. The transformer head is intentionally shallow so
it does not overfit when fine-tuned on the smaller, demographically tailored
Indian-subcontinent datasets (InFER++, ISED, ISSED, AIIMS toolbox).

The architecture is:

    Image (B, 3, 224, 224)
      → ResNet-50 trunk (ImageNet pretrained) → feature map (B, 2048, 7, 7)
      → 1x1 projection                         → (B, vit_dim, 7, 7)
      → flatten + prepend CLS + positional emb → (B, 50, vit_dim)
      → Transformer encoder (vit_depth blocks)
      → take CLS token, LayerNorm, Linear      → logits (B, num_classes)

The encoder uses pre-norm blocks (norm_first=True) and stochastic depth
(DropPath) — both standard ViT regularisers that materially help small-data
fine-tuning. We implement DropPath inline rather than pulling in `timm` so the
model has zero external dependencies beyond torch + torchvision.
"""

from __future__ import annotations

from typing import Optional

import torch
import torch.nn as nn
import torch.nn.functional as F
from torchvision.models import ResNet50_Weights, resnet50

from .config import EMOTION_CLASSES, MODEL_CONFIG, MODEL_IMAGE_SIZE, ModelConfig







def _drop_path(x: torch.Tensor, drop_prob: float, training: bool) -> torch.Tensor:
    """Stochastic depth: randomly drops entire residual branches per-sample."""
    if drop_prob <= 0.0 or not training:
        return x
    keep_prob = 1.0 - drop_prob
    
    
    shape = (x.shape[0],) + (1,) * (x.ndim - 1)
    mask = x.new_empty(shape).bernoulli_(keep_prob).div_(keep_prob)
    return x * mask


class DropPath(nn.Module):
    def __init__(self, drop_prob: float = 0.0) -> None:
        super().__init__()
        self.drop_prob = float(drop_prob)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return _drop_path(x, self.drop_prob, self.training)


class MultiHeadSelfAttention(nn.Module):
    """Standard multi-head self-attention with separate q, k, v projections.

    Implemented explicitly (not via ``nn.MultiheadAttention``) so the dropouts
    behave exactly like the reference ViT and so we can read intermediate
    attention maps later for explainability/audit work.
    """

    def __init__(self, dim: int, num_heads: int, attn_dropout: float = 0.0, proj_dropout: float = 0.0):
        super().__init__()
        if dim % num_heads != 0:
            raise ValueError(f"dim ({dim}) must be divisible by num_heads ({num_heads})")
        self.num_heads = num_heads
        self.head_dim = dim // num_heads
        self.scale = self.head_dim ** -0.5

        self.qkv = nn.Linear(dim, dim * 3, bias=True)
        self.attn_drop = nn.Dropout(attn_dropout)
        self.proj = nn.Linear(dim, dim)
        self.proj_drop = nn.Dropout(proj_dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        b, n, c = x.shape
        qkv = self.qkv(x).reshape(b, n, 3, self.num_heads, self.head_dim)
        qkv = qkv.permute(2, 0, 3, 1, 4)  
        q, k, v = qkv[0], qkv[1], qkv[2]

        attn = (q @ k.transpose(-2, -1)) * self.scale
        attn = attn.softmax(dim=-1)
        attn = self.attn_drop(attn)

        out = (attn @ v).transpose(1, 2).reshape(b, n, c)
        out = self.proj(out)
        out = self.proj_drop(out)
        return out


class TransformerBlock(nn.Module):
    """Pre-norm ViT block: x = x + DropPath(Attn(Norm(x))); x = x + DropPath(MLP(Norm(x)))."""

    def __init__(
        self,
        dim: int,
        num_heads: int,
        mlp_ratio: float,
        dropout: float,
        attn_dropout: float,
        drop_path: float,
    ) -> None:
        super().__init__()
        self.norm1 = nn.LayerNorm(dim)
        self.attn = MultiHeadSelfAttention(dim, num_heads, attn_dropout=attn_dropout, proj_dropout=dropout)
        self.drop_path1 = DropPath(drop_path)

        self.norm2 = nn.LayerNorm(dim)
        hidden = int(dim * mlp_ratio)
        self.mlp = nn.Sequential(
            nn.Linear(dim, hidden),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden, dim),
            nn.Dropout(dropout),
        )
        self.drop_path2 = DropPath(drop_path)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.drop_path1(self.attn(self.norm1(x)))
        x = x + self.drop_path2(self.mlp(self.norm2(x)))
        return x







class HybridResNetViT(nn.Module):
    """ResNet-50 trunk + small ViT head for facial emotion recognition.

    Args:
        config: a ``ModelConfig`` controlling head capacity and partial
            fine-tuning. If ``None``, the package default ``MODEL_CONFIG`` is
            used.
        num_classes: override for the head output dimension. Defaults to
            ``config.num_classes`` (7 for the canonical FER label space).
        pretrained: whether to initialise the ResNet-50 trunk from the
            ImageNet1K_V2 weights bundled with torchvision. Disable for unit
            tests so the constructor doesn't try to fetch weights.
    """

    def __init__(
        self,
        config: Optional[ModelConfig] = None,
        num_classes: Optional[int] = None,
        pretrained: bool = True,
    ) -> None:
        super().__init__()
        self.config = config or MODEL_CONFIG
        nc = num_classes if num_classes is not None else self.config.num_classes
        self.num_classes = nc

        
        weights = ResNet50_Weights.IMAGENET1K_V2 if pretrained else None
        backbone = resnet50(weights=weights)
        
        
        self.cnn_stem = nn.Sequential(
            backbone.conv1,
            backbone.bn1,
            backbone.relu,
            backbone.maxpool,
            backbone.layer1,
            backbone.layer2,
        )
        self.cnn_top = nn.Sequential(
            backbone.layer3,
            backbone.layer4,
        )
        
        backbone_channels = 2048
        feature_grid = MODEL_IMAGE_SIZE // 32  
        self.num_patches = feature_grid * feature_grid

        if self.config.partial_finetune:
            for p in self.cnn_stem.parameters():
                p.requires_grad = False

        
        self.projection = nn.Conv2d(backbone_channels, self.config.vit_dim, kernel_size=1)

        self.cls_token = nn.Parameter(torch.zeros(1, 1, self.config.vit_dim))
        self.pos_embed = nn.Parameter(torch.zeros(1, self.num_patches + 1, self.config.vit_dim))
        self.pos_drop = nn.Dropout(self.config.dropout)

        
        dpr = [x.item() for x in torch.linspace(0, self.config.drop_path, self.config.vit_depth)]
        self.blocks = nn.ModuleList(
            [
                TransformerBlock(
                    dim=self.config.vit_dim,
                    num_heads=self.config.vit_heads,
                    mlp_ratio=self.config.vit_mlp_ratio,
                    dropout=self.config.dropout,
                    attn_dropout=self.config.attn_dropout,
                    drop_path=dpr[i],
                )
                for i in range(self.config.vit_depth)
            ]
        )
        self.norm = nn.LayerNorm(self.config.vit_dim)

        
        self.head = nn.Linear(self.config.vit_dim, nc)

        self._init_weights()

    
    def _init_weights(self) -> None:
        
        
        
        nn.init.trunc_normal_(self.cls_token, std=0.02)
        nn.init.trunc_normal_(self.pos_embed, std=0.02)
        for m in [self.projection, self.head, *self.blocks.modules()]:
            if isinstance(m, nn.Linear):
                nn.init.trunc_normal_(m.weight, std=0.02)
                if m.bias is not None:
                    nn.init.zeros_(m.bias)
            elif isinstance(m, nn.Conv2d):
                nn.init.kaiming_normal_(m.weight, mode="fan_out", nonlinearity="relu")
                if m.bias is not None:
                    nn.init.zeros_(m.bias)
            elif isinstance(m, nn.LayerNorm):
                nn.init.ones_(m.weight)
                nn.init.zeros_(m.bias)

    
    def forward_features(self, x: torch.Tensor) -> torch.Tensor:
        """Return the pre-classification CLS embedding (B, vit_dim)."""
        
        if self.config.partial_finetune:
            
            
            
            
            self.cnn_stem.eval()
            with torch.no_grad():
                feat = self.cnn_stem(x)
        else:
            feat = self.cnn_stem(x)
        feat = self.cnn_top(feat)               
        feat = self.projection(feat)            
        b, d, h, w = feat.shape
        feat = feat.flatten(2).transpose(1, 2)  

        cls = self.cls_token.expand(b, -1, -1)
        feat = torch.cat([cls, feat], dim=1)    
        feat = self.pos_drop(feat + self.pos_embed)

        for block in self.blocks:
            feat = block(feat)
        feat = self.norm(feat)
        return feat[:, 0]                       

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.head(self.forward_features(x))

    
    @torch.no_grad()
    def predict_probs(self, x: torch.Tensor) -> torch.Tensor:
        """Softmax-normalised class probabilities. Convenience for inference."""
        self.eval()
        logits = self.forward(x)
        return F.softmax(logits, dim=-1)

    
    def parameter_groups(self, lr_backbone: float, lr_head: float) -> list[dict]:
        """Two-group parameter list for the optimizer.

        The ResNet trunk gets ``lr_backbone`` (typically ~10x smaller) so the
        pretrained features are nudged rather than overwritten; the ViT head +
        projection + classifier get ``lr_head``.
        """
        backbone_params = [p for p in self.cnn_top.parameters() if p.requires_grad]
        if not self.config.partial_finetune:
            backbone_params += [p for p in self.cnn_stem.parameters() if p.requires_grad]
        head_params = (
            list(self.projection.parameters())
            + [self.cls_token, self.pos_embed]
            + list(self.blocks.parameters())
            + list(self.norm.parameters())
            + list(self.head.parameters())
        )
        return [
            {"params": backbone_params, "lr": lr_backbone, "name": "backbone"},
            {"params": head_params, "lr": lr_head, "name": "head"},
        ]







def build_default_model(pretrained: bool = True) -> HybridResNetViT:
    """Return a HybridResNetViT with the canonical 7-class head."""
    return HybridResNetViT(
        config=MODEL_CONFIG,
        num_classes=len(EMOTION_CLASSES),
        pretrained=pretrained,
    )
