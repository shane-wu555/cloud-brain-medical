"""
Train a 2D U-Net for hemorrhage/lesion segmentation.

Expected dataset layout, produced by prepare_seg_cq500.py:

    lesion_seg_dataset/
      images_nii/
        segcq500_0001.nii.gz
      labels_nii/
        segcq500_0001.nii.gz

Labels:
    0 = background
    1 = hemorrhage/lesion

Usage:
    python ai-service/training/train_lesion_segmentation.py \
      --data /root/autodl-tmp/lesion_seg_dataset \
      --output /root/autodl-tmp/models/lesion_segmentation
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

import numpy as np
import torch
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from torch.utils.data import DataLoader

import train_metal_segmentation as shared


def main() -> None:
    parser = argparse.ArgumentParser(description="Train lesion segmentation UNet")
    parser.add_argument("--data", required=True, help="Dataset root with images_nii/ and labels_nii/")
    parser.add_argument("--output", required=True, help="Output model directory")
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--image-size", type=int, default=512)
    parser.add_argument("--base-channels", type=int, default=32)
    parser.add_argument("--neg-ratio", type=float, default=1.5)
    parser.add_argument("--val-ratio", type=float, default=0.2)
    parser.add_argument("--patience", type=int, default=15)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--hu-min", type=float, default=-100.0, help="Lower HU clip for brain/hemorrhage")
    parser.add_argument("--hu-max", type=float, default=300.0, help="Upper HU clip for brain/hemorrhage")
    parser.add_argument("--threshold", type=float, default=0.35, help="Validation mask threshold")
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    shared.HU_MIN = float(args.hu_min)
    shared.HU_MAX = float(args.hu_max)

    data_dir = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    items = shared.scan_dataset(data_dir, neg_ratio=args.neg_ratio, seed=args.seed)
    if not items:
        raise RuntimeError("No training slices found.")
    train_items, val_items = shared.split_by_case(items, args.val_ratio, args.seed)
    shared.save_split_csv(train_items, val_items, output_dir)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_ds = shared.MetalSliceDataset(train_items, image_size=args.image_size, augment=True)
    val_ds = shared.MetalSliceDataset(val_items, image_size=args.image_size, augment=False)
    train_loader = DataLoader(
        train_ds,
        batch_size=args.batch,
        shuffle=True,
        num_workers=args.workers,
        pin_memory=device.type == "cuda",
    )
    val_loader = DataLoader(
        val_ds,
        batch_size=args.batch,
        shuffle=False,
        num_workers=args.workers,
        pin_memory=device.type == "cuda",
    )

    pos_weight = shared.compute_pos_weight(train_items)
    model = shared.UNet2D(in_channels=1, base_channels=args.base_channels).to(device)
    criterion = shared.DiceBceLoss(pos_weight=pos_weight)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-6)
    scaler = torch.cuda.amp.GradScaler(enabled=device.type == "cuda")

    print(f"Train slices: {len(train_items)} | Val slices: {len(val_items)} | device={device}")
    print(f"HU window: [{args.hu_min}, {args.hu_max}] | pos_weight={pos_weight:.3f}")

    best_dice = 0.0
    bad_epochs = 0
    history: list[dict] = []
    for epoch in range(1, args.epochs + 1):
        train_metrics = shared.train_one_epoch(model, train_loader, optimizer, criterion, scaler, device)
        val_metrics = _evaluate_with_threshold(model, val_loader, criterion, device, args.threshold)
        scheduler.step()

        row = {
            "epoch": epoch,
            "lr": optimizer.param_groups[0]["lr"],
            **{f"train_{k}": v for k, v in train_metrics.items()},
            **{f"val_{k}": v for k, v in val_metrics.items()},
        }
        history.append(row)
        (output_dir / "history.json").write_text(json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8")

        torch.save(
            {
                "epoch": epoch,
                "model": model.state_dict(),
                "optimizer": optimizer.state_dict(),
                "best_dice": best_dice,
                "args": vars(args),
            },
            output_dir / "last_lesion_segmentation.pt",
        )

        print(
            f"Epoch {epoch:03d} | train={train_metrics['loss']:.4f} "
            f"| val={val_metrics['loss']:.4f} | dice={val_metrics['dice']:.4f} "
            f"| iou={val_metrics['iou']:.4f} | recall={val_metrics['recall']:.4f} "
            f"| fg={val_metrics['fg_ratio']:.6f}"
        )

        if val_metrics["dice"] > best_dice:
            best_dice = val_metrics["dice"]
            bad_epochs = 0
            torch.save(model.state_dict(), output_dir / "best_lesion_segmentation.pt")
            print(f"  saved best dice={best_dice:.4f}")
        else:
            bad_epochs += 1
            if bad_epochs >= args.patience:
                print(f"Early stopping: no Dice improvement for {args.patience} epochs")
                break

    model.load_state_dict(torch.load(output_dir / "best_lesion_segmentation.pt", map_location=device))
    model.eval()
    dummy = torch.randn(1, 1, args.image_size, args.image_size, device=device)
    torch.onnx.export(
        model,
        dummy,
        str(output_dir / "lesion_segmentation.onnx"),
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    print(f"Done. best_dice={best_dice:.4f}. Output: {output_dir}")


@torch.no_grad()
def _evaluate_with_threshold(model, loader, criterion, device, threshold: float) -> dict[str, float]:
    model.eval()
    total_loss = total_dice_loss = total_bce_loss = 0.0
    agg = {"dice": 0.0, "iou": 0.0, "recall": 0.0, "fg_ratio": 0.0}
    for batch in shared.tqdm(loader, desc="val", leave=False):
        image = batch["image"].to(device, non_blocking=True)
        mask = batch["mask"].to(device, non_blocking=True)
        logits = model(image)
        loss, dice_loss, bce_loss = criterion(logits, mask)
        total_loss += loss.item()
        total_dice_loss += dice_loss.item()
        total_bce_loss += bce_loss.item()
        metrics = shared.metrics_from_logits(logits, mask, threshold=threshold)
        for key in agg:
            agg[key] += metrics[key]
    n = max(len(loader), 1)
    return {
        "loss": total_loss / n,
        "dice_loss": total_dice_loss / n,
        "bce_loss": total_bce_loss / n,
        **{key: value / n for key, value in agg.items()},
    }


if __name__ == "__main__":
    main()
