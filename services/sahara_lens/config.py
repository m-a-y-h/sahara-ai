"""Configuration constants for the Sahara Lens FER pipeline.

All hyperparameters, label orderings, and training defaults live here so
training, evaluation, and inference share one source of truth. Override per
environment by importing the module and reassigning attributes before the
training script reads them.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Sequence










EMOTION_CLASSES: tuple[str, ...] = (
    "anger",
    "disgust",
    "fear",
    "happiness",
    "neutral",
    "sadness",
    "surprise",
)






SCREENING_CLASSES: tuple[str, ...] = (
    "neutral",
    "stress",
    "sadness",
    "fear",
)





NEG_EMOTION_GROUP: tuple[str, ...] = ("stress", "sadness", "fear")







MODEL_IMAGE_SIZE: int = 224



IMAGENET_MEAN: tuple[float, float, float] = (0.485, 0.456, 0.406)
IMAGENET_STD: tuple[float, float, float] = (0.229, 0.224, 0.225)







@dataclass(frozen=True)
class ModelConfig:
    """Hyperparameters for the hybrid ResNet-50 + ViT architecture.

    Defaults target ~95M parameters: a manageable footprint for fine-tuning on
    a single GPU while leaving headroom in the ViT head for the dataset
    adaptation step. ``vit_depth=4`` is intentionally shallow — research shows
    that on top of a strong CNN backbone, a small transformer head closes most
    of the gap to deeper hybrid models without the over-fitting risk of a
    deep, randomly-initialised transformer on a small dataset.
    """

    num_classes: int = len(EMOTION_CLASSES)
    vit_dim: int = 768
    vit_depth: int = 4
    vit_heads: int = 12
    vit_mlp_ratio: float = 4.0
    dropout: float = 0.1
    attn_dropout: float = 0.1
    drop_path: float = 0.1
    
    
    
    
    partial_finetune: bool = True







@dataclass(frozen=True)
class TrainConfig:
    """Defaults for ``train.py``. Override on the command line."""

    
    
    data_root: Path = Path("data/inferpp")
    output_dir: Path = Path("sahara_lens/checkpoints")

    epochs: int = 60
    batch_size: int = 32
    grad_accum_steps: int = 1

    
    
    lr_backbone: float = 1e-5
    lr_head: float = 1e-4
    weight_decay: float = 0.05
    warmup_epochs: int = 3
    label_smoothing: float = 0.1

    
    use_randaugment: bool = True
    randaug_n: int = 2
    randaug_m: int = 9
    use_random_erasing: bool = True
    use_mixup: bool = True
    mixup_alpha: float = 0.2

    
    use_weighted_sampler: bool = True
    use_class_weighted_loss: bool = True

    
    
    early_stop_patience: int = 10

    num_workers: int = 4
    seed: int = 1337
    device: str = "cuda"  







@dataclass(frozen=True)
class ScreeningConfig:
    """Thresholds the screening layer uses to label the user state.

    Tune these on a held-out validation split per deployment cohort. The
    defaults are deliberately *conservative* (favour false negatives over
    false positives) because a false positive routes a user to a counselor
    alert, which has a real social cost in the SAHARA AI deployment context.
    """

    
    
    min_top_class_prob: float = 0.35

    
    
    elevated_threshold: float = 0.45
    high_threshold: float = 0.65

    
    
    
    stress_proxy_weight: float = 0.5



MODEL_CONFIG: ModelConfig = ModelConfig()
TRAIN_CONFIG: TrainConfig = TrainConfig()
SCREENING_CONFIG: ScreeningConfig = ScreeningConfig()


def class_index(name: str, classes: Sequence[str] = EMOTION_CLASSES) -> int:
    """Lookup a class index by name. Raises ValueError on unknown labels."""
    try:
        return classes.index(name)
    except ValueError as e:
        raise ValueError(
            f"Unknown class '{name}'. Known classes: {list(classes)}"
        ) from e
