"""
Train a 2D U-Net for metal/artifact segmentation.

Expected dataset layout, produced by annotate_metal_masks.py:

    metal_mask_dataset/
      images_nii/
        case001.nii.gz
      labels_nii/
        case001.nii.gz

Labels are binary by default:
    0 = background
    1 = metal/artifact

Usage:
    python training/train_metal_segmentation.py \
      --data /autodl-tmp/metal_mask_dataset \
      --output /autodl-tmp/models/metal_segmentation \
      --epochs 50 --batch 8
"""

from __future__ import annotations

import argparse
import csv
import json
import random
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np
import SimpleITK as sitk
import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from torch.utils.data import DataLoader, Dataset
from tqdm import tqdm


HU_MIN = -1000.0
HU_MAX = 3000.0


@dataclass(frozen=True)
class SliceItem:
    image_path: str
    mask_path: str
    case_id: str
    slice_index: int
    has_mask: bool


class DoubleConv(nn.Module):
    def __init__(self, in_channels: int, out_channels: int):
        super().__init__()
        self.block = nn.Sequential(
            nn.Conv2d(in_channels, out_channels, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.ReLU(inplace=True),
            nn.Conv2d(out_channels, out_channels, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.ReLU(inplace=True),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.block(x)


class UNet2D(nn.Module):
    def __init__(self, in_channels: int = 1, base_channels: int = 32):
        super().__init__()
        c = base_channels
        self.pool = nn.MaxPool2d(2)

        self.enc1 = DoubleConv(in_channels, c)
        self.enc2 = DoubleConv(c, c * 2)
        self.enc3 = DoubleConv(c * 2, c * 4)
        self.enc4 = DoubleConv(c * 4, c * 8)

        self.up3 = nn.ConvTranspose2d(c * 8, c * 4, 2, stride=2)
        self.dec3 = DoubleConv(c * 8, c * 4)
        self.up2 = nn.ConvTranspose2d(c * 4, c * 2, 2, stride=2)
        self.dec2 = DoubleConv(c * 4, c * 2)
        self.up1 = nn.ConvTranspose2d(c * 2, c, 2, stride=2)
        self.dec1 = DoubleConv(c * 2, c)
        self.head = nn.Conv2d(c, 1, 1)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x1 = self.enc1(x)
        x2 = self.enc2(self.pool(x1))
        x3 = self.enc3(self.pool(x2))
        x4 = self.enc4(self.pool(x3))

        y = self.up3(x4)
        y = self.dec3(torch.cat([y, x3], dim=1))
        y = self.up2(y)
        y = self.dec2(torch.cat([y, x2], dim=1))
        y = self.up1(y)
        y = self.dec1(torch.cat([y, x1], dim=1))
        return self.head(y)


class DiceBceLoss(nn.Module):
    def __init__(self, pos_weight: float = 1.0, dice_weight: float = 0.7):
        super().__init__()
        self.dice_weight = dice_weight
        self.bce = nn.BCEWithLogitsLoss(pos_weight=torch.tensor([pos_weight], dtype=torch.float32))

    def forward(self, logits: torch.Tensor, target: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        if self.bce.pos_weight.device != logits.device:
            self.bce.pos_weight = self.bce.pos_weight.to(logits.device)
        bce = self.bce(logits, target)
        prob = torch.sigmoid(logits)
        intersection = (prob * target).sum(dim=(1, 2, 3))
        union = prob.sum(dim=(1, 2, 3)) + target.sum(dim=(1, 2, 3))
        dice_loss = 1.0 - ((2.0 * intersection + 1e-6) / (union + 1e-6)).mean()
        total = self.dice_weight * dice_loss + (1.0 - self.dice_weight) * bce
        return total, dice_loss.detach(), bce.detach()


class MetalSliceDataset(Dataset):
    def __init__(self, items: list[SliceItem], image_size: int = 512, augment: bool = False):
        self.items = items
        self.image_size = image_size
        self.augment = augment

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, idx: int) -> dict[str, torch.Tensor]:
        item = self.items[idx]
        image_vol, mask_vol = _load_pair(item.image_path, item.mask_path)
        image = image_vol[item.slice_index]
        mask = (mask_vol[item.slice_index] > 0).astype(np.float32)

        image = np.clip(image.astype(np.float32), HU_MIN, HU_MAX)
        image = (image - HU_MIN) / (HU_MAX - HU_MIN)
        image = cv2.resize(image, (self.image_size, self.image_size), interpolation=cv2.INTER_LINEAR)
        mask = cv2.resize(mask, (self.image_size, self.image_size), interpolation=cv2.INTER_NEAREST)

        if self.augment:
            image, mask = _augment(image, mask)

        image_t = torch.from_numpy(image[None].astype(np.float32))
        mask_t = torch.from_numpy(mask[None].astype(np.float32))
        return {"image": image_t, "mask": mask_t}


@lru_cache(maxsize=12)
def _load_pair(image_path: str, mask_path: str) -> tuple[np.ndarray, np.ndarray]:
    image = sitk.GetArrayFromImage(sitk.ReadImage(image_path)).astype(np.float32)
    mask = sitk.GetArrayFromImage(sitk.ReadImage(mask_path)).astype(np.uint8)
    if image.ndim == 2:
        image = image[np.newaxis]
    if mask.ndim == 2:
        mask = mask[np.newaxis]
    if image.shape != mask.shape:
        raise ValueError(f"Image/mask shape mismatch: {image_path} {image.shape} vs {mask.shape}")
    return image, mask


def _augment(image: np.ndarray, mask: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    if random.random() < 0.5:
        image = np.flip(image, axis=1)
        mask = np.flip(mask, axis=1)
    if random.random() < 0.25:
        image = np.flip(image, axis=0)
        mask = np.flip(mask, axis=0)
    k = random.randint(0, 3)
    if k:
        image = np.rot90(image, k)
        mask = np.rot90(mask, k)
    return np.ascontiguousarray(image), np.ascontiguousarray(mask)


def scan_dataset(data_dir: Path, neg_ratio: float, seed: int) -> list[SliceItem]:
    images_dir = data_dir / "images_nii"
    labels_dir = data_dir / "labels_nii"
    if not images_dir.exists() or not labels_dir.exists():
        raise FileNotFoundError(f"Expected images_nii/ and labels_nii/ under {data_dir}")

    items_pos: list[SliceItem] = []
    items_neg: list[SliceItem] = []
    image_files = sorted(p for p in images_dir.iterdir() if p.name.endswith((".nii", ".nii.gz")))
    for image_path in tqdm(image_files, desc="Scanning masks"):
        mask_path = labels_dir / image_path.name
        if not mask_path.exists():
            mask_path = labels_dir / image_path.name.replace("_ct.nii.gz", "_mask.nii.gz")
        if not mask_path.exists():
            tqdm.write(f"[skip] missing mask: {image_path.name}")
            continue
        _, mask = _load_pair(str(image_path), str(mask_path))
        per_slice = (mask > 0).reshape(mask.shape[0], -1).sum(axis=1)
        for z, count in enumerate(per_slice):
            item = SliceItem(str(image_path), str(mask_path), image_path.name.replace(".nii.gz", ""), int(z), bool(count > 0))
            if count > 0:
                items_pos.append(item)
            else:
                items_neg.append(item)

    rng = random.Random(seed)
    rng.shuffle(items_neg)
    n_neg = min(len(items_neg), int(max(len(items_pos), 1) * neg_ratio))
    items = items_pos + items_neg[:n_neg]
    rng.shuffle(items)
    print(f"Positive slices: {len(items_pos)} | sampled negative slices: {n_neg} | total: {len(items)}")
    return items


def split_by_case(items: list[SliceItem], val_ratio: float, seed: int) -> tuple[list[SliceItem], list[SliceItem]]:
    cases = sorted({item.case_id for item in items})
    rng = random.Random(seed)
    rng.shuffle(cases)
    n_val = max(1, int(len(cases) * val_ratio))
    val_cases = set(cases[:n_val])
    train_items = [item for item in items if item.case_id not in val_cases]
    val_items = [item for item in items if item.case_id in val_cases]
    return train_items, val_items


def compute_pos_weight(items: list[SliceItem], max_cases: int = 24) -> float:
    total_pos = 0.0
    total_neg = 0.0
    seen: set[tuple[str, str]] = set()
    for item in items:
        key = (item.image_path, item.mask_path)
        if key in seen:
            continue
        seen.add(key)
        _, mask = _load_pair(item.image_path, item.mask_path)
        binary = mask > 0
        total_pos += float(binary.sum())
        total_neg += float(binary.size - binary.sum())
        if len(seen) >= max_cases:
            break
    pos_weight = total_neg / max(total_pos, 1.0)
    return float(min(max(pos_weight, 1.0), 30.0))


def metrics_from_logits(logits: torch.Tensor, target: torch.Tensor, threshold: float = 0.25) -> dict[str, float]:
    pred = (torch.sigmoid(logits) > threshold).float()
    intersection = (pred * target).sum().item()
    pred_sum = pred.sum().item()
    target_sum = target.sum().item()
    dice = (2 * intersection + 1e-6) / (pred_sum + target_sum + 1e-6)
    iou = (intersection + 1e-6) / (pred_sum + target_sum - intersection + 1e-6)
    recall = (intersection + 1e-6) / (target_sum + 1e-6)
    fg_ratio = pred_sum / (target.numel() + 1e-6)
    return {"dice": dice, "iou": iou, "recall": recall, "fg_ratio": fg_ratio}


def train_one_epoch(model, loader, optimizer, criterion, scaler, device) -> dict[str, float]:
    model.train()
    use_amp = device.type == "cuda"
    total_loss = total_dice = total_bce = 0.0
    for batch in tqdm(loader, desc="train", leave=False):
        image = batch["image"].to(device, non_blocking=True)
        mask = batch["mask"].to(device, non_blocking=True)
        optimizer.zero_grad(set_to_none=True)
        with torch.cuda.amp.autocast(enabled=use_amp):
            logits = model(image)
            loss, dice_loss, bce_loss = criterion(logits, mask)
        scaler.scale(loss).backward()
        scaler.unscale_(optimizer)
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        scaler.step(optimizer)
        scaler.update()
        total_loss += loss.item()
        total_dice += dice_loss.item()
        total_bce += bce_loss.item()
    n = max(len(loader), 1)
    return {"loss": total_loss / n, "dice_loss": total_dice / n, "bce_loss": total_bce / n}


@torch.no_grad()
def evaluate(model, loader, criterion, device) -> dict[str, float]:
    model.eval()
    total_loss = total_dice_loss = total_bce_loss = 0.0
    agg = {"dice": 0.0, "iou": 0.0, "recall": 0.0, "fg_ratio": 0.0}
    for batch in tqdm(loader, desc="val", leave=False):
        image = batch["image"].to(device, non_blocking=True)
        mask = batch["mask"].to(device, non_blocking=True)
        logits = model(image)
        loss, dice_loss, bce_loss = criterion(logits, mask)
        total_loss += loss.item()
        total_dice_loss += dice_loss.item()
        total_bce_loss += bce_loss.item()
        m = metrics_from_logits(logits, mask)
        for key in agg:
            agg[key] += m[key]
    n = max(len(loader), 1)
    return {
        "loss": total_loss / n,
        "dice_loss": total_dice_loss / n,
        "bce_loss": total_bce_loss / n,
        **{key: value / n for key, value in agg.items()},
    }


def save_split_csv(train_items: list[SliceItem], val_items: list[SliceItem], output_dir: Path) -> None:
    rows = []
    for split, items in [("train", train_items), ("val", val_items)]:
        for item in items:
            rows.append({
                "split": split,
                "case_id": item.case_id,
                "slice_index": item.slice_index,
                "has_mask": int(item.has_mask),
                "image_path": item.image_path,
                "mask_path": item.mask_path,
            })
    with open(output_dir / "split_manifest.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train metal artifact segmentation UNet")
    parser.add_argument("--data", required=True, help="Dataset root with images_nii/ and labels_nii/")
    parser.add_argument("--output", required=True, help="Output model directory")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--image-size", type=int, default=512)
    parser.add_argument("--base-channels", type=int, default=32)
    parser.add_argument("--neg-ratio", type=float, default=2.0)
    parser.add_argument("--val-ratio", type=float, default=0.2)
    parser.add_argument("--patience", type=int, default=10)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    data_dir = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    items = scan_dataset(data_dir, neg_ratio=args.neg_ratio, seed=args.seed)
    if not items:
        raise RuntimeError("No training slices found.")
    train_items, val_items = split_by_case(items, args.val_ratio, args.seed)
    save_split_csv(train_items, val_items, output_dir)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_ds = MetalSliceDataset(train_items, image_size=args.image_size, augment=True)
    val_ds = MetalSliceDataset(val_items, image_size=args.image_size, augment=False)
    train_loader = DataLoader(train_ds, batch_size=args.batch, shuffle=True, num_workers=args.workers, pin_memory=device.type == "cuda")
    val_loader = DataLoader(val_ds, batch_size=args.batch, shuffle=False, num_workers=args.workers, pin_memory=device.type == "cuda")

    pos_weight = compute_pos_weight(train_items)
    model = UNet2D(in_channels=1, base_channels=args.base_channels).to(device)
    criterion = DiceBceLoss(pos_weight=pos_weight)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-6)
    scaler = torch.cuda.amp.GradScaler(enabled=device.type == "cuda")

    print(f"Train slices: {len(train_items)} | Val slices: {len(val_items)} | device={device}")
    print(f"pos_weight={pos_weight:.3f}")

    best_dice = 0.0
    bad_epochs = 0
    history: list[dict] = []
    for epoch in range(1, args.epochs + 1):
        train_metrics = train_one_epoch(model, train_loader, optimizer, criterion, scaler, device)
        val_metrics = evaluate(model, val_loader, criterion, device)
        scheduler.step()

        row = {
            "epoch": epoch,
            "lr": optimizer.param_groups[0]["lr"],
            **{f"train_{k}": v for k, v in train_metrics.items()},
            **{f"val_{k}": v for k, v in val_metrics.items()},
        }
        history.append(row)
        (output_dir / "history.json").write_text(json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8")

        torch.save({
            "epoch": epoch,
            "model": model.state_dict(),
            "optimizer": optimizer.state_dict(),
            "best_dice": best_dice,
            "args": vars(args),
        }, output_dir / "last_metal_segmentation.pt")

        print(
            f"Epoch {epoch:03d} | train={train_metrics['loss']:.4f} "
            f"| val={val_metrics['loss']:.4f} | dice={val_metrics['dice']:.4f} "
            f"| iou={val_metrics['iou']:.4f} | recall={val_metrics['recall']:.4f} "
            f"| fg={val_metrics['fg_ratio']:.6f}"
        )

        if val_metrics["dice"] > best_dice:
            best_dice = val_metrics["dice"]
            bad_epochs = 0
            torch.save(model.state_dict(), output_dir / "best_metal_segmentation.pt")
            print(f"  saved best dice={best_dice:.4f}")
        else:
            bad_epochs += 1
            if bad_epochs >= args.patience:
                print(f"Early stopping: no Dice improvement for {args.patience} epochs")
                break

    model.load_state_dict(torch.load(output_dir / "best_metal_segmentation.pt", map_location=device))
    model.eval()
    dummy = torch.randn(1, 1, args.image_size, args.image_size, device=device)
    torch.onnx.export(
        model,
        dummy,
        str(output_dir / "metal_segmentation.onnx"),
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    print(f"Done. best_dice={best_dice:.4f}. Output: {output_dir}")


if __name__ == "__main__":
    main()
