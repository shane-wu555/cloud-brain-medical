"""
阶段②：EfficientNet-B7 + RadImageNet 二分类训练（正常/出血）
使用 MONAI 数据管道 + timm 模型 + Focal Loss + 5-fold 交叉验证

RadImageNet 权重默认自动下载（首次运行约 250MB）。
下载来源：https://github.com/BMEII-AI/RadImageNet

用法:
    python training/train_classifier.py \
        --data   /root/autodl-tmp/cq500_processed \
        --output /root/autodl-tmp/models/classifier \
        --fold   0 \
        --epochs 50
"""

import argparse
import csv
import json
import os
from pathlib import Path

import numpy as np
import timm
import torch
import torch.nn as nn
from monai.data import DataLoader, Dataset
from monai.transforms import (
    Compose,
    EnsureChannelFirstd,
    NormalizeIntensityd,
    RandAffined,
    RandFlipd,
    RandRotate90d,
    Resized,
    ScaleIntensityRanged,
    ToTensord,
    LoadImaged,
    EnsureTyped,
)
from sklearn.metrics import classification_report, roc_auc_score
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from tqdm import tqdm

# CQ500 只有出血/正常两类标签
CLASSES = ["normal", "hemorrhage"]
NUM_CLASSES = len(CLASSES)


class FocalLoss(nn.Module):
    def __init__(self, gamma: float = 2.0, weight: torch.Tensor | None = None):
        super().__init__()
        self.gamma = gamma
        self.weight = weight

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        ce = nn.functional.cross_entropy(logits, targets, weight=self.weight, reduction="none")
        pt = torch.exp(-ce)
        return ((1 - pt) ** self.gamma * ce).mean()


# RadImageNet EfficientNetB7 权重（Google Drive）
# 来源：https://github.com/BMEII-AI/RadImageNet
_RADIMGNET_GDRIVE_ID = "1CqSsaXBBriCQBZMWvAlZdqNDiMpRcLtZ"
_RADIMGNET_CACHE     = Path.home() / ".cache" / "radimgnet" / "efficientnet_b7.pth"


def _download_radimgnet() -> Path | None:
    """首次运行时自动下载 RadImageNet EfficientNetB7 权重"""
    if _RADIMGNET_CACHE.exists():
        return _RADIMGNET_CACHE
    try:
        import gdown
        _RADIMGNET_CACHE.parent.mkdir(parents=True, exist_ok=True)
        print("下载 RadImageNet EfficientNetB7 权重（约 250MB，首次运行）...")
        gdown.download(id=_RADIMGNET_GDRIVE_ID, output=str(_RADIMGNET_CACHE), quiet=False)
        return _RADIMGNET_CACHE
    except Exception as e:
        print(f"RadImageNet 下载失败，回退到 ImageNet 权重: {e}")
        print("提示：pip install gdown 后重试，或手动下载放到:", _RADIMGNET_CACHE)
        return None


def build_model() -> nn.Module:
    """
    EfficientNet-B7 + RadImageNet 预训练权重。
    RadImageNet 是在 1.35M 张医学影像上训练的，比 ImageNet 更适合 CT 迁移学习。
    首次运行自动下载权重；下载失败自动回退到 ImageNet。
    """
    # 先用 ImageNet 权重初始化（保证结构正确）
    model = timm.create_model("efficientnet_b7", pretrained=True,
                               num_classes=NUM_CLASSES)

    # 尝试加载 RadImageNet 权重替换骨干网络
    rad_path = _download_radimgnet()
    if rad_path:
        ckpt = torch.load(rad_path, map_location="cpu")
        # RadImageNet 权重是 1000 类的，classifier 层维度不同，strict=False 跳过
        missing, _ = model.load_state_dict(ckpt, strict=False)
        print(f"RadImageNet 权重加载完成（分类头重新初始化，共 {len(missing)} 层）")
    else:
        print("使用 ImageNet 预训练权重")

    return model


def get_transforms(is_train: bool) -> Compose:
    base = [
        LoadImaged(keys=["image"], image_only=True),
        EnsureChannelFirstd(keys=["image"]),       # (C, H, W)
        Resized(keys=["image"], spatial_size=(512, 512)),
        NormalizeIntensityd(keys=["image"], nonzero=True, channel_wise=True),
        EnsureTyped(keys=["image"], dtype=torch.float32),
    ]
    if is_train:
        aug = [
            RandFlipd(keys=["image"], prob=0.5, spatial_axis=1),
            RandRotate90d(keys=["image"], prob=0.3, max_k=3),
            RandAffined(
                keys=["image"], prob=0.5,
                rotate_range=(0.26, 0.26),
                scale_range=(0.1, 0.1),
                translate_range=(20, 20),
                mode="bilinear", padding_mode="zeros",
            ),
        ]
        return Compose(base + aug)
    return Compose(base)


def load_manifest(data_dir: Path, fold: int | None, is_train: bool) -> list[dict]:
    manifest_path = data_dir / "manifest.csv"
    with open(manifest_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    if fold is None:
        # 全量训练：用所有数据
        return rows if is_train else []

    label_map = {"normal": 0, "hemorrhage": 1, "ischemia": 2}
    filtered = []
    for r in rows:
        in_val = int(r["fold"]) == fold
        if is_train and not in_val:
            filtered.append({
                "image": str(data_dir / "images" / r["slice_name"]),
                "label": label_map[r["label"]],
            })
        elif not is_train and in_val:
            filtered.append({
                "image": str(data_dir / "images" / r["slice_name"]),
                "label": label_map[r["label"]],
            })
    return filtered


def compute_class_weights(items: list[dict]) -> torch.Tensor:
    counts = np.bincount([it["label"] for it in items], minlength=NUM_CLASSES).astype(float)
    weights = 1.0 / (counts + 1e-6)
    return torch.tensor(weights / weights.sum() * NUM_CLASSES, dtype=torch.float32)


def train_epoch(model, loader, optimizer, criterion, device) -> float:
    model.train()
    total_loss = 0.0
    for batch in tqdm(loader, desc="训练", leave=False):
        x = batch["image"].to(device)
        y = batch["label"].to(device)
        optimizer.zero_grad()
        loss = criterion(model(x), y)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)


@torch.no_grad()
def eval_epoch(model, loader, device) -> tuple[float, np.ndarray, np.ndarray]:
    model.eval()
    all_preds, all_labels, all_probs = [], [], []
    for batch in tqdm(loader, desc="验证", leave=False):
        x = batch["image"].to(device)
        logits = model(x)
        probs = torch.softmax(logits, dim=1).cpu().numpy()
        preds = probs.argmax(axis=1)
        all_probs.extend(probs.tolist())
        all_preds.extend(preds.tolist())
        all_labels.extend(batch["label"].numpy().tolist())
    acc = np.mean(np.array(all_preds) == np.array(all_labels))
    return acc, np.array(all_preds), np.array(all_labels)


def main() -> None:
    parser = argparse.ArgumentParser(description="EfficientNet-B0 CT 分类训练")
    parser.add_argument("--data",    required=True)
    parser.add_argument("--output",  required=True)
    parser.add_argument("--fold",    type=int, default=None, help="0~4，None=全量训练")
    parser.add_argument("--epochs",  type=int, default=50)
    parser.add_argument("--batch",   type=int,   default=16)   # B7 更大，显存更多
    parser.add_argument("--lr",     type=float, default=1e-4)
    parser.add_argument("--workers",type=int,   default=8)
    args = parser.parse_args()

    data_dir   = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"使用设备: {device}")

    train_items = load_manifest(data_dir, args.fold, is_train=True)
    val_items   = load_manifest(data_dir, args.fold, is_train=False)
    print(f"训练集: {len(train_items)} | 验证集: {len(val_items)}")

    train_ds = Dataset(data=train_items, transform=get_transforms(is_train=True))
    val_ds   = Dataset(data=val_items,   transform=get_transforms(is_train=False))
    train_dl = DataLoader(train_ds, batch_size=args.batch, shuffle=True,
                          num_workers=args.workers, pin_memory=True)
    val_dl   = DataLoader(val_ds,   batch_size=args.batch, shuffle=False,
                          num_workers=args.workers, pin_memory=True)

    model     = build_model().to(device)
    weights   = compute_class_weights(train_items).to(device)
    criterion = FocalLoss(gamma=2.0, weight=weights)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-6)

    best_acc = 0.0
    history  = []

    for epoch in range(1, args.epochs + 1):
        train_loss = train_epoch(model, train_dl, optimizer, criterion, device)
        scheduler.step()

        if val_items:
            val_acc, preds, labels = eval_epoch(model, val_dl, device)
            print(f"Epoch {epoch:03d} | loss={train_loss:.4f} | val_acc={val_acc:.4f}")
            history.append({"epoch": epoch, "loss": train_loss, "val_acc": val_acc})

            if val_acc > best_acc:
                best_acc = val_acc
                torch.save(model.state_dict(), output_dir / "best_classifier.pt")
                print(f"  ✓ 保存最优模型 acc={best_acc:.4f}")
        else:
            print(f"Epoch {epoch:03d} | loss={train_loss:.4f}")
            torch.save(model.state_dict(), output_dir / "best_classifier.pt")

    # 导出 ONNX（推理服务使用）
    print("导出 ONNX...")
    model.load_state_dict(torch.load(output_dir / "best_classifier.pt", map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, 512, 512).to(device)  # B7 支持任意输入尺寸
    onnx_path = output_dir / "classifier.onnx"
    torch.onnx.export(
        model, dummy, str(onnx_path),
        input_names=["input"], output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    print(f"ONNX 已保存: {onnx_path}")

    # 最终评估报告
    if val_items:
        _, final_preds, final_labels = eval_epoch(model, val_dl, device)
        report = classification_report(final_labels, final_preds,
                                       target_names=CLASSES, digits=4)
        print("\n分类报告:\n", report)
        (output_dir / "val_report.txt").write_text(report, encoding="utf-8")

    (output_dir / "history.json").write_text(json.dumps(history, indent=2), encoding="utf-8")
    print(f"\n训练完成，最优 acc={best_acc:.4f}，模型目录: {output_dir}")


if __name__ == "__main__":
    main()
