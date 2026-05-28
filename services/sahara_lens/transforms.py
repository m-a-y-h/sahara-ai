"""Image preprocessing transforms for training and inference.

Train-time augmentation deliberately matches the ViT/DeiT recipe (RandAugment +
RandomErasing) rather than the heavier ConvNeXt recipe. On small FER datasets
the heavier recipe tends to wash out the subtle action-unit cues we need to
discriminate fear from surprise and sadness from neutral.
"""

from __future__ import annotations

from typing import Callable

from PIL import Image
from torchvision import transforms as T

from .config import IMAGENET_MEAN, IMAGENET_STD, MODEL_IMAGE_SIZE, TRAIN_CONFIG







def build_train_transform(image_size: int = MODEL_IMAGE_SIZE) -> Callable[[Image.Image], "T.functional.Tensor"]:
    """Augmentation pipeline used during fine-tuning.

    Notes:
        * RandomResizedCrop scale lower-bound is 0.8 — faces fill most of the
          frame in InFER++ and aggressive cropping clips the eyes/brows that
          drive fear and sadness signals.
        * Horizontal flip is safe for FER (emotions are roughly symmetric).
        * ColorJitter is mild because we want the model to learn skin-tone
          robustness from the *data*, not from heavy augmentation that can
          drift the colour distribution away from realistic South Asian
          lighting conditions.
        * RandAugment / RandomErasing are gated on the train config so
          ablations can disable them without editing this file.
    """
    augmentations: list = [
        T.RandomResizedCrop(image_size, scale=(0.8, 1.0), ratio=(0.9, 1.1)),
        T.RandomHorizontalFlip(p=0.5),
        T.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.1, hue=0.02),
    ]

    if TRAIN_CONFIG.use_randaugment:
        augmentations.append(
            T.RandAugment(num_ops=TRAIN_CONFIG.randaug_n, magnitude=TRAIN_CONFIG.randaug_m)
        )

    augmentations += [
        T.ToTensor(),
        T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ]

    if TRAIN_CONFIG.use_random_erasing:
        augmentations.append(T.RandomErasing(p=0.25, value="random"))

    return T.Compose(augmentations)







def build_eval_transform(image_size: int = MODEL_IMAGE_SIZE) -> Callable[[Image.Image], "T.functional.Tensor"]:
    """Deterministic resize + center-crop for validation, test, and inference.

    Resize-shorter-edge to 256 then center-crop to 224 is the standard
    ImageNet evaluation protocol — it preserves a small border that contains
    forehead/jawline context the model uses during attention.
    """
    short_side = int(image_size * 256 / 224)
    return T.Compose(
        [
            T.Resize(short_side, interpolation=T.InterpolationMode.BICUBIC),
            T.CenterCrop(image_size),
            T.ToTensor(),
            T.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ]
    )







def mixup_batch(
    images: "T.functional.Tensor",
    targets: "T.functional.Tensor",
    num_classes: int,
    alpha: float,
):
    """Apply Mixup to a training batch.

    Returns the blended images plus the soft target distribution. Use with
    a cross-entropy that accepts soft labels (PyTorch ≥ 1.10 supports this
    natively via ``F.cross_entropy(input, target_dist)``).
    """
    import torch

    if alpha <= 0.0:
        return images, torch.nn.functional.one_hot(targets, num_classes).float()

    lam = float(torch.distributions.Beta(alpha, alpha).sample().item())
    perm = torch.randperm(images.size(0), device=images.device)

    mixed_images = lam * images + (1.0 - lam) * images[perm]
    one_hot = torch.nn.functional.one_hot(targets, num_classes).float()
    one_hot_perm = torch.nn.functional.one_hot(targets[perm], num_classes).float()
    mixed_targets = lam * one_hot + (1.0 - lam) * one_hot_perm
    return mixed_images, mixed_targets
