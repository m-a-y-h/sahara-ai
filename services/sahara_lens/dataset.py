"""Dataset loaders for facial emotion recognition.

The expected on-disk layout is the torchvision ImageFolder convention:

    data_root/
        train/
            anger/    *.jpg
            disgust/  *.jpg
            fear/     *.jpg
            happiness/*.jpg
            neutral/  *.jpg
            sadness/  *.jpg
            surprise/ *.jpg
        val/   <same subfolders>
        test/  <same subfolders>

This matches the typical InFER++ release layout. If your local copy uses a
different folder name (e.g. ``happy`` instead of ``happiness``), pass a
``class_aliases`` mapping to ``EmotionImageFolder``.

Datasets are not bundled with this repo — request InFER++ from the authors and
place it under ``data/inferpp/``. Secondary datasets (ISED, ISSED, AIIMS) can
be appended via ``torch.utils.data.ConcatDataset``.
"""

from __future__ import annotations

import os
from collections import Counter
from pathlib import Path
from typing import Callable, Iterable, Optional, Sequence

import torch
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from torchvision.datasets import ImageFolder

from .config import EMOTION_CLASSES







class EmotionImageFolder(ImageFolder):
    """ImageFolder that fixes the class index ordering.

    ``torchvision.ImageFolder`` orders classes alphabetically by folder name,
    which silently varies across datasets (``anger`` vs ``Anger`` vs ``angry``)
    and would break checkpoint compatibility. We normalise everything to the
    package-canonical ``EMOTION_CLASSES`` ordering and accept a
    ``class_aliases`` dict to handle folder-name variants.
    """

    def __init__(
        self,
        root: str | os.PathLike,
        transform: Optional[Callable] = None,
        class_aliases: Optional[dict[str, str]] = None,
    ) -> None:
        self._aliases = {k.lower(): v for k, v in (class_aliases or {}).items()}
        super().__init__(str(root), transform=transform)

    
    def find_classes(self, directory: str) -> tuple[list[str], dict[str, int]]:
        present = sorted(entry.name for entry in os.scandir(directory) if entry.is_dir())
        if not present:
            raise FileNotFoundError(f"No class subfolders found under {directory}")

        normalised: dict[str, str] = {}
        for folder in present:
            canonical = self._aliases.get(folder.lower(), folder.lower())
            if canonical not in EMOTION_CLASSES:
                raise ValueError(
                    f"Folder '{folder}' maps to unknown class '{canonical}'. "
                    f"Known classes: {list(EMOTION_CLASSES)}. Pass a class_aliases "
                    f"dict if your dataset uses different folder names."
                )
            normalised[folder] = canonical

        # Always use the FIXED canonical index, even if the dataset is missing a
        # class (e.g. no 'disgust' images). Indexing by the present-subset would
        # silently shift indices, mismatch the model's num_classes, and corrupt
        # the screening mapping at inference.
        class_to_idx: dict[str, int] = {
            folder: EMOTION_CLASSES.index(canonical)
            for folder, canonical in normalised.items()
        }
        return list(EMOTION_CLASSES), class_to_idx







def class_frequencies(dataset: ImageFolder) -> dict[int, int]:
    """Count samples per class index for an ImageFolder-style dataset."""
    counts = Counter(label for _, label in dataset.samples)
    return dict(counts)


def build_class_weights(dataset: ImageFolder, beta: float = 0.9999) -> torch.Tensor:
    """Class-Balanced Loss weights (Cui et al., 2019).

    The formula is ``w_c = (1 - beta) / (1 - beta^n_c)``. With ``beta`` close
    to 1.0 it approaches inverse-frequency weighting; lower values smoothly
    interpolate toward uniform weights. Returned tensor is ordered by the
    *output* class index (so it can be passed directly to
    ``nn.CrossEntropyLoss(weight=...)``).
    """
    counts = class_frequencies(dataset)
    num_classes = max(counts.keys()) + 1
    weights = torch.zeros(num_classes)
    for cls_idx, n in counts.items():
        if n == 0:
            continue
        effective_num = 1.0 - (beta ** n)
        weights[cls_idx] = (1.0 - beta) / effective_num
    
    
    weights = weights * (num_classes / weights.sum())
    return weights


def build_weighted_sampler(dataset: ImageFolder) -> WeightedRandomSampler:
    """Per-sample weights for a WeightedRandomSampler — inverse class frequency.

    Use this for training datasets where minority classes (fear, disgust on
    InFER++) are 5–10x smaller than the majority (happiness). It draws each
    minibatch so every class is *expected* to appear equally often, which is
    the single biggest lever for raising per-class F1 on the negative emotions.
    """
    counts = class_frequencies(dataset)
    per_class_weight = {cls: 1.0 / max(n, 1) for cls, n in counts.items()}
    sample_weights = torch.tensor(
        [per_class_weight[label] for _, label in dataset.samples], dtype=torch.double
    )
    return WeightedRandomSampler(sample_weights, num_samples=len(sample_weights), replacement=True)







def build_dataloaders(
    data_root: str | os.PathLike,
    train_transform: Callable,
    eval_transform: Callable,
    batch_size: int,
    num_workers: int,
    use_weighted_sampler: bool = True,
    class_aliases: Optional[dict[str, str]] = None,
) -> tuple[DataLoader, DataLoader, Optional[DataLoader]]:
    """Build train/val/test loaders. ``test`` is None if no ``test/`` folder."""
    root = Path(data_root)
    train_ds = EmotionImageFolder(root / "train", transform=train_transform, class_aliases=class_aliases)
    val_ds = EmotionImageFolder(root / "val", transform=eval_transform, class_aliases=class_aliases)

    if use_weighted_sampler:
        sampler = build_weighted_sampler(train_ds)
        train_loader = DataLoader(
            train_ds,
            batch_size=batch_size,
            sampler=sampler,
            num_workers=num_workers,
            pin_memory=True,
            drop_last=True,
        )
    else:
        train_loader = DataLoader(
            train_ds,
            batch_size=batch_size,
            shuffle=True,
            num_workers=num_workers,
            pin_memory=True,
            drop_last=True,
        )

    val_loader = DataLoader(
        val_ds, batch_size=batch_size, shuffle=False, num_workers=num_workers, pin_memory=True
    )

    test_loader: Optional[DataLoader] = None
    test_root = root / "test"
    if test_root.exists():
        test_ds = EmotionImageFolder(test_root, transform=eval_transform, class_aliases=class_aliases)
        test_loader = DataLoader(
            test_ds, batch_size=batch_size, shuffle=False, num_workers=num_workers, pin_memory=True
        )

    return train_loader, val_loader, test_loader







def describe_dataset(dataset: ImageFolder) -> str:
    """Return a short human-readable per-class breakdown.

    Useful for printing at the top of training so the operator can sanity-check
    the class distribution they're about to train on.
    """
    counts = class_frequencies(dataset)
    idx_to_class = {v: k for k, v in dataset.class_to_idx.items()}
    total = sum(counts.values())
    lines = [f"  {idx_to_class[i]:10s}  {n:6d}  ({100.0 * n / total:5.1f}%)" for i, n in sorted(counts.items())]
    header = f"Dataset: {len(dataset)} images across {len(counts)} classes"
    return header + "\n" + "\n".join(lines)
