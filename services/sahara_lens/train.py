"""Fine-tuning loop for the Hybrid ResNet-50 + ViT FER model.

Run from the repository root:

    python -m sahara_lens.train \
        --data-root data/inferpp \
        --output-dir sahara_lens/checkpoints/run_01 \
        --epochs 60 --batch-size 32

The script:

    1. Builds train/val (and optional test) DataLoaders with class-balanced
       sampling and the train-time augmentation recipe from ``transforms.py``.
    2. Constructs ``HybridResNetViT`` with the trunk in partial-finetune mode.
    3. Optimises with AdamW + cosine LR schedule + linear warmup.
    4. Loss: cross-entropy with class weights (Cui et al. 2019), label
       smoothing, and optional Mixup.
    5. Per-epoch evaluation reports per-class precision/recall/F1 plus the
       "minimum F1 over (stress, sadness, fear)" — the ship-gate metric.
    6. Early stops on the ship-gate metric and saves the best checkpoint.

This is a reference implementation tuned for a single GPU (8-12 GB VRAM).
For Colab T4 / Kaggle P100 the defaults work out of the box. Multi-GPU
support is intentionally left out — fine-tuning on InFER++-scale data does
not benefit enough from data-parallel to justify the complexity here.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import time
from pathlib import Path
from typing import Any, Optional

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader

from .config import (
    EMOTION_CLASSES,
    MODEL_CONFIG,
    NEG_EMOTION_GROUP,
    SCREENING_CLASSES,
    SCREENING_CONFIG,
    TRAIN_CONFIG,
    TrainConfig,
    class_index,
)
from .dataset import build_class_weights, build_dataloaders, describe_dataset
from .evaluate import evaluate
from .model import HybridResNetViT
from .screening import emotion_to_screening
from .transforms import build_eval_transform, build_train_transform, mixup_batch







def seed_everything(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    
    
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False







def cosine_warmup_lr(epoch: float, warmup_epochs: float, total_epochs: float) -> float:
    """Multiplier in [0, 1] applied to each parameter group's base LR.

    Linear warmup from 0 → 1 over ``warmup_epochs`` epochs, then cosine decay
    from 1 → 0 over the remaining ``total_epochs - warmup_epochs``.
    """
    if epoch < warmup_epochs:
        return max(epoch / max(warmup_epochs, 1.0), 1e-3)
    progress = (epoch - warmup_epochs) / max(total_epochs - warmup_epochs, 1.0)
    return 0.5 * (1.0 + math.cos(math.pi * min(progress, 1.0)))


def apply_lr(optimizer: torch.optim.Optimizer, multiplier: float) -> None:
    for group in optimizer.param_groups:
        if "base_lr" not in group:
            group["base_lr"] = group["lr"]
        group["lr"] = group["base_lr"] * multiplier







def ship_gate_metric(per_class_f1: dict[str, float]) -> float:
    """Minimum F1 over the three negative emotions in the *screening* space.

    The model trains on 7 emotion classes but we ship on the 4-class screening
    space, so the metric is computed against the screening-class F1s that the
    evaluator returns. A high overall accuracy is not enough — the model has
    to perform on every one of (stress, sadness, fear) before it ships.
    """
    return min(per_class_f1.get(c, 0.0) for c in NEG_EMOTION_GROUP)







def train_one_epoch(
    model: HybridResNetViT,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    criterion: nn.Module,
    device: torch.device,
    num_classes: int,
    use_mixup: bool,
    mixup_alpha: float,
    grad_accum_steps: int,
    epoch: int,
    total_epochs: int,
    warmup_epochs: int,
) -> dict[str, float]:
    model.train()
    running_loss = 0.0
    running_correct = 0
    running_seen = 0

    steps_per_epoch = max(len(loader), 1)
    optimizer.zero_grad(set_to_none=True)

    for step, (images, targets) in enumerate(loader):
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)

        
        
        frac_epoch = epoch + step / steps_per_epoch
        apply_lr(optimizer, cosine_warmup_lr(frac_epoch, warmup_epochs, total_epochs))

        if use_mixup:
            mixed_images, mixed_soft = mixup_batch(images, targets, num_classes, mixup_alpha)
            logits = model(mixed_images)
            
            
            loss = -(mixed_soft * F.log_softmax(logits, dim=-1)).sum(dim=-1).mean()
        else:
            logits = model(images)
            loss = criterion(logits, targets)

        
        
        (loss / grad_accum_steps).backward()
        if (step + 1) % grad_accum_steps == 0:
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            optimizer.zero_grad(set_to_none=True)

        with torch.no_grad():
            running_loss += float(loss.item()) * images.size(0)
            preds = logits.argmax(dim=-1)
            running_correct += int((preds == targets).sum().item())
            running_seen += images.size(0)

    return {
        "loss": running_loss / max(running_seen, 1),
        "acc": running_correct / max(running_seen, 1),
    }







def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Fine-tune Sahara Lens FER model.")
    p.add_argument("--data-root", type=Path, default=TRAIN_CONFIG.data_root)
    p.add_argument("--output-dir", type=Path, default=TRAIN_CONFIG.output_dir)
    p.add_argument("--epochs", type=int, default=TRAIN_CONFIG.epochs)
    p.add_argument("--batch-size", type=int, default=TRAIN_CONFIG.batch_size)
    p.add_argument("--grad-accum-steps", type=int, default=TRAIN_CONFIG.grad_accum_steps)
    p.add_argument("--lr-backbone", type=float, default=TRAIN_CONFIG.lr_backbone)
    p.add_argument("--lr-head", type=float, default=TRAIN_CONFIG.lr_head)
    p.add_argument("--weight-decay", type=float, default=TRAIN_CONFIG.weight_decay)
    p.add_argument("--warmup-epochs", type=int, default=TRAIN_CONFIG.warmup_epochs)
    p.add_argument("--label-smoothing", type=float, default=TRAIN_CONFIG.label_smoothing)
    p.add_argument("--no-mixup", action="store_true", help="Disable Mixup augmentation.")
    p.add_argument("--no-weighted-sampler", action="store_true", help="Disable WeightedRandomSampler.")
    p.add_argument("--no-class-weighted-loss", action="store_true", help="Disable class-weighted CE loss.")
    p.add_argument("--early-stop-patience", type=int, default=TRAIN_CONFIG.early_stop_patience)
    p.add_argument("--num-workers", type=int, default=TRAIN_CONFIG.num_workers)
    p.add_argument("--seed", type=int, default=TRAIN_CONFIG.seed)
    p.add_argument(
        "--device",
        type=str,
        default=TRAIN_CONFIG.device,
        help="torch device id ('cuda', 'cpu', 'mps').",
    )
    p.add_argument(
        "--class-aliases",
        type=str,
        default=None,
        help='JSON of folder-name overrides, e.g. \'{"happy":"happiness","angry":"anger"}\'',
    )
    return p.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> None:
    args = parse_args(argv)
    seed_everything(args.seed)

    device = torch.device(args.device if torch.cuda.is_available() or args.device != "cuda" else "cpu")
    if str(device) != args.device:
        print(f"[train] requested device '{args.device}' unavailable; using '{device}' instead.")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    class_aliases = json.loads(args.class_aliases) if args.class_aliases else None

    
    train_tf = build_train_transform()
    eval_tf = build_eval_transform()
    train_loader, val_loader, test_loader = build_dataloaders(
        data_root=args.data_root,
        train_transform=train_tf,
        eval_transform=eval_tf,
        batch_size=args.batch_size,
        num_workers=args.num_workers,
        use_weighted_sampler=not args.no_weighted_sampler,
        class_aliases=class_aliases,
    )
    print(describe_dataset(train_loader.dataset))

    
    model = HybridResNetViT(config=MODEL_CONFIG, num_classes=len(EMOTION_CLASSES))
    model.to(device)

    
    if not args.no_class_weighted_loss:
        class_weights = build_class_weights(train_loader.dataset).to(device)
        print(f"[train] class weights: {[round(float(w), 3) for w in class_weights.tolist()]}")
    else:
        class_weights = None

    criterion = nn.CrossEntropyLoss(weight=class_weights, label_smoothing=args.label_smoothing)

    optimizer = torch.optim.AdamW(
        model.parameter_groups(lr_backbone=args.lr_backbone, lr_head=args.lr_head),
        weight_decay=args.weight_decay,
    )

    
    best_ship_gate = -1.0
    best_path = args.output_dir / "best.pt"
    history_path = args.output_dir / "history.jsonl"
    epochs_without_improvement = 0

    for epoch in range(args.epochs):
        t0 = time.perf_counter()

        train_metrics = train_one_epoch(
            model=model,
            loader=train_loader,
            optimizer=optimizer,
            criterion=criterion,
            device=device,
            num_classes=len(EMOTION_CLASSES),
            use_mixup=not args.no_mixup,
            mixup_alpha=TRAIN_CONFIG.mixup_alpha,
            grad_accum_steps=args.grad_accum_steps,
            epoch=epoch,
            total_epochs=args.epochs,
            warmup_epochs=args.warmup_epochs,
        )

        val_metrics = evaluate(model, val_loader, device=device)
        ship_gate = ship_gate_metric(val_metrics["screening_f1_per_class"])
        elapsed = time.perf_counter() - t0

        log_entry: dict[str, Any] = {
            "epoch": epoch,
            "elapsed_s": round(elapsed, 1),
            "train": train_metrics,
            "val": val_metrics,
            "ship_gate_min_neg_f1": ship_gate,
        }
        with history_path.open("a") as f:
            f.write(json.dumps(log_entry) + "\n")

        print(
            f"[epoch {epoch:3d}/{args.epochs}] "
            f"train_loss={train_metrics['loss']:.4f} "
            f"train_acc={train_metrics['acc']:.3f} "
            f"val_acc={val_metrics['top1']:.3f} "
            f"ship_gate={ship_gate:.3f} ({elapsed:.0f}s)"
        )

        if ship_gate > best_ship_gate:
            best_ship_gate = ship_gate
            epochs_without_improvement = 0
            torch.save(
                {
                    "model_state_dict": model.state_dict(),
                    "model_config": vars(MODEL_CONFIG),
                    "screening_config": vars(SCREENING_CONFIG),
                    "emotion_classes": list(EMOTION_CLASSES),
                    "screening_classes": list(SCREENING_CLASSES),
                    "epoch": epoch,
                    "ship_gate": ship_gate,
                    "val_metrics": val_metrics,
                },
                best_path,
            )
            print(f"[train] new best ship-gate {ship_gate:.4f} → {best_path}")
        else:
            epochs_without_improvement += 1
            if epochs_without_improvement >= args.early_stop_patience:
                print(
                    f"[train] no ship-gate improvement for {epochs_without_improvement} epochs; stopping."
                )
                break

    
    if test_loader is not None and best_path.exists():
        print(f"\n[train] reloading best checkpoint ({best_path}) for final test evaluation.")
        ckpt = torch.load(best_path, map_location=device)
        model.load_state_dict(ckpt["model_state_dict"])
        test_metrics = evaluate(model, test_loader, device=device)
        print("[train] final test metrics:")
        print(json.dumps(test_metrics, indent=2))
        with (args.output_dir / "test_metrics.json").open("w") as f:
            json.dump(test_metrics, f, indent=2)


if __name__ == "__main__":
    main()
