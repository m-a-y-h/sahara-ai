"""Evaluation utilities.

Computes top-1 accuracy, the full per-class precision/recall/F1 in *both* the
7-class raw emotion space and the 4-class screening space, and the confusion
matrix. The screening-space F1s are the operational ship-gate metrics — they
are what the front end actually consumes.

A simple subgroup-F1 hook is also provided so that when demographic labels
are available (e.g. for a fairness audit on Pakistani vs. Indian vs. mixed
subgroups), per-subgroup metrics can be computed with the same primitives.
"""

from __future__ import annotations

from collections import defaultdict
from typing import Iterable, Optional

import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader

from .config import EMOTION_CLASSES, SCREENING_CLASSES, SCREENING_CONFIG
from .screening import emotion_to_screening







def confusion_matrix(targets: torch.Tensor, preds: torch.Tensor, num_classes: int) -> torch.Tensor:
    """``cm[i, j]`` is the number of samples with true label ``i``, predicted ``j``."""
    cm = torch.zeros(num_classes, num_classes, dtype=torch.long)
    pair = targets * num_classes + preds
    counts = torch.bincount(pair, minlength=num_classes * num_classes)
    cm = counts.view(num_classes, num_classes)
    return cm


def per_class_prf1(cm: torch.Tensor) -> tuple[list[float], list[float], list[float]]:
    """Per-class (precision, recall, F1) from a confusion matrix."""
    cm = cm.float()
    tp = torch.diagonal(cm)
    fp = cm.sum(dim=0) - tp
    fn = cm.sum(dim=1) - tp

    precision = tp / (tp + fp).clamp_min(1e-12)
    recall = tp / (tp + fn).clamp_min(1e-12)
    f1 = 2 * precision * recall / (precision + recall).clamp_min(1e-12)
    return precision.tolist(), recall.tolist(), f1.tolist()







def _project_to_screening(prob_row: torch.Tensor) -> tuple[int, list[float]]:
    """Convert a 7-vector of raw emotion probs to a screening class index + dist.

    Returns ``(screening_index, screening_distribution)``. ``screening_index``
    is the argmax of the screening distribution, used to compute confusion
    matrices in screening space.
    """
    raw_dict = {cls: float(prob_row[i].item()) for i, cls in enumerate(EMOTION_CLASSES)}
    screening = emotion_to_screening(raw_dict, cfg=SCREENING_CONFIG)
    ordered = [screening[c] for c in SCREENING_CLASSES]
    return int(max(range(len(ordered)), key=lambda i: ordered[i])), ordered


def _project_target_to_screening(target_idx: int) -> int:
    """Map a 7-class ground-truth label to the corresponding screening index.

    Used so screening-space metrics compare like-for-like rather than
    comparing model predictions to raw-class labels.

    The mapping mirrors ``screening.emotion_to_screening`` at argmax level:

        happiness, neutral, surprise → 'neutral'
        anger, disgust               → 'stress'
        sadness                      → 'sadness'
        fear                         → 'fear'
    """
    raw = EMOTION_CLASSES[target_idx]
    if raw in {"happiness", "neutral", "surprise"}:
        return SCREENING_CLASSES.index("neutral")
    if raw in {"anger", "disgust"}:
        return SCREENING_CLASSES.index("stress")
    if raw == "sadness":
        return SCREENING_CLASSES.index("sadness")
    if raw == "fear":
        return SCREENING_CLASSES.index("fear")
    raise ValueError(f"Unmappable raw class {raw}")







@torch.no_grad()
def evaluate(
    model,
    loader: DataLoader,
    device: torch.device,
    subgroups: Optional[Iterable[int]] = None,
) -> dict:
    """Run the model over ``loader`` and return all metrics as a JSON-friendly dict.

    Args:
        model: a ``HybridResNetViT``.
        loader: validation/test DataLoader.
        device: torch device the model lives on.
        subgroups: optional per-sample subgroup labels (any hashable). If
            provided, per-subgroup screening F1 is included in the output for
            fairness auditing. Same length and order as the dataset.
    """
    model.eval()
    all_targets: list[int] = []
    all_preds: list[int] = []
    all_screening_targets: list[int] = []
    all_screening_preds: list[int] = []
    correct = 0
    total = 0

    for images, targets in loader:
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)
        logits = model(images)
        probs = F.softmax(logits, dim=-1).cpu()
        preds = probs.argmax(dim=-1)

        for row_probs, tgt, pred in zip(probs, targets.cpu(), preds):
            tgt_i = int(tgt.item())
            pred_i = int(pred.item())
            all_targets.append(tgt_i)
            all_preds.append(pred_i)
            screening_pred, _ = _project_to_screening(row_probs)
            screening_tgt = _project_target_to_screening(tgt_i)
            all_screening_preds.append(screening_pred)
            all_screening_targets.append(screening_tgt)
            correct += int(pred_i == tgt_i)
            total += 1

    targets_t = torch.tensor(all_targets, dtype=torch.long)
    preds_t = torch.tensor(all_preds, dtype=torch.long)
    cm_emotion = confusion_matrix(targets_t, preds_t, num_classes=len(EMOTION_CLASSES))
    prec_e, rec_e, f1_e = per_class_prf1(cm_emotion)

    screening_targets_t = torch.tensor(all_screening_targets, dtype=torch.long)
    screening_preds_t = torch.tensor(all_screening_preds, dtype=torch.long)
    cm_screening = confusion_matrix(screening_targets_t, screening_preds_t, num_classes=len(SCREENING_CLASSES))
    prec_s, rec_s, f1_s = per_class_prf1(cm_screening)

    result: dict = {
        "top1": correct / max(total, 1),
        "n_samples": total,
        "emotion_confusion_matrix": cm_emotion.tolist(),
        "emotion_precision_per_class": {c: prec_e[i] for i, c in enumerate(EMOTION_CLASSES)},
        "emotion_recall_per_class": {c: rec_e[i] for i, c in enumerate(EMOTION_CLASSES)},
        "emotion_f1_per_class": {c: f1_e[i] for i, c in enumerate(EMOTION_CLASSES)},
        "screening_confusion_matrix": cm_screening.tolist(),
        "screening_precision_per_class": {c: prec_s[i] for i, c in enumerate(SCREENING_CLASSES)},
        "screening_recall_per_class": {c: rec_s[i] for i, c in enumerate(SCREENING_CLASSES)},
        "screening_f1_per_class": {c: f1_s[i] for i, c in enumerate(SCREENING_CLASSES)},
        "screening_macro_f1": sum(f1_s) / len(f1_s),
    }

    if subgroups is not None:
        subgroups_list = list(subgroups)
        if len(subgroups_list) != total:
            raise ValueError(
                f"subgroups has length {len(subgroups_list)} but evaluated {total} samples."
            )
        per_group_records: dict[int, list[tuple[int, int]]] = defaultdict(list)
        for s, t, p in zip(subgroups_list, all_screening_targets, all_screening_preds):
            per_group_records[s].append((t, p))
        per_group_f1: dict[str, dict[str, float]] = {}
        for group, records in per_group_records.items():
            tt = torch.tensor([r[0] for r in records], dtype=torch.long)
            pp = torch.tensor([r[1] for r in records], dtype=torch.long)
            cm_g = confusion_matrix(tt, pp, num_classes=len(SCREENING_CLASSES))
            _, _, f1_g = per_class_prf1(cm_g)
            per_group_f1[str(group)] = {c: f1_g[i] for i, c in enumerate(SCREENING_CLASSES)}
        result["subgroup_screening_f1"] = per_group_f1

    return result
