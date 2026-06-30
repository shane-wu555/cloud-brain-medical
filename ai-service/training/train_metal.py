"""
金属伪影检测训练
============================
数据来源：preprocess_cq500.py 输出的目录（manifest.csv + images/*.png）

标签生成策略：
  不需要原始 DICOM，直接从 PNG 的骨窗通道检测金属。
  骨窗参数: WW=2500 WL=480 → lo=-770 hi=1730
    · HU 1000 对应像素值 ≈ 181 → 阈值 181 识别金属区
    · 再用连通域面积过滤掉噪声（< MIN_METAL_PIXELS）

  每张切片的覆盖率决定标签（切片级，而非体积级）：
    coverage == 0           → normal        (0)
    0 < coverage < 0.05%    → small         (1)  [binary: artifact]
    0.05% ≤ coverage < 0.5% → moderate      (2)  [binary: artifact]
    coverage ≥ 0.5%         → severe        (3)  [binary: artifact]

训练模式：
  --mode binary   : 有无金属（先用这个，数据少时更可靠）
  --mode severity : 四级严重度（需要足够病例）

用法:
    # 解压 tar 包
    tar -xf cq500_train.tar -C /data/cq500_processed

    # 二分类（推荐先跑）
    python training/train_metal.py \\
        --data   /data/cq500_processed \\
        --output /models/metal_binary \\
        --mode   binary --epochs 40

    # 四分类（数据充足后）
    python training/train_metal.py \\
        --data   /data/cq500_processed \\
        --output /models/metal_severity \\
        --mode   severity --epochs 50
"""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import timm
import torch
import torch.nn as nn
from monai.data import DataLoader, Dataset
from monai.transforms import (
    Compose, EnsureChannelFirstd, EnsureTyped, LoadImaged,
    NormalizeIntensityd, RandAffined, RandFlipd, RandRotate90d, Resized,
)
from sklearn.metrics import classification_report
from tqdm import tqdm
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR

# ── 类别 ──────────────────────────────────────────────────────────────────────
SEVERITY_CLASSES = ["normal", "small_metal", "moderate_metal", "severe_metal"]
BINARY_CLASSES   = ["normal", "metal_artifact"]

# ── 骨窗金属检测参数 ───────────────────────────────────────────────────────────
# 骨窗: WW=2500, WL=480 → lo=-770, hi=1730
# HU 1000 → pixel = (1000+770)/2500*255 ≈ 181
METAL_PIXEL_THRESH  = 181    # 骨窗像素值 > 此值 → 判定为金属
MIN_METAL_PIXELS    = 20     # 连通域面积 < 此值 → 噪声，过滤掉
SMALL_COVERAGE      = 0.05   # % 覆盖率阈值：small_metal
MODERATE_COVERAGE   = 0.50   # % 覆盖率阈值：moderate_metal


# ─────────────────────────────────────────────────────────────────────────────
# 从 PNG 骨窗通道检测金属
# ─────────────────────────────────────────────────────────────────────────────

def detect_metal_from_png(png_path: Path) -> float:
    """
    从骨窗通道估算金属体素覆盖率（%）。

    preprocess_cq500.py 保存方式：
      cv2.imwrite(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR))
      rgb 顺序: [brain, blood, bone]
      cvtColor RGB→BGR 后: [bone, blood, brain]（B=bone, G=blood, R=brain）
    cv2.imread 读回为 BGR：
      img[:,:,0] = B = bone window ← 这就是我们要的通道
    """
    img = cv2.imread(str(png_path))
    if img is None:
        return 0.0

    bone_ch = img[:, :, 0].astype(np.uint8)   # 骨窗通道
    metal_raw = (bone_ch > METAL_PIXEL_THRESH).astype(np.uint8)

    # 去噪：连通域面积过滤
    num_labels, label_map, stats, _ = cv2.connectedComponentsWithStats(metal_raw)
    clean = np.zeros_like(metal_raw)
    for i in range(1, num_labels):
        if stats[i, cv2.CC_STAT_AREA] >= MIN_METAL_PIXELS:
            clean[label_map == i] = 1

    return float(clean.sum()) / clean.size * 100


def coverage_to_label(pct: float, mode: str) -> int:
    if mode == "binary":
        return 0 if pct == 0 else 1
    # severity
    if pct == 0:
        return 0
    elif pct < SMALL_COVERAGE:
        return 1
    elif pct < MODERATE_COVERAGE:
        return 2
    else:
        return 3


# ─────────────────────────────────────────────────────────────────────────────
# 生成带金属标签的清单
# ─────────────────────────────────────────────────────────────────────────────

def build_metal_manifest(data_dir: Path, output_dir: Path, mode: str) -> Path:
    """
    读取 preprocess_cq500.py 的 manifest.csv，
    为每张切片从骨窗检测金属标签，生成 metal_manifest_<mode>.csv。
    """
    src_manifest = data_dir / "manifest.csv"
    if not src_manifest.exists():
        raise FileNotFoundError(
            f"找不到 manifest.csv: {src_manifest}\n"
            f"请先解压 cq500_train.tar 到 {data_dir}"
        )

    metal_manifest = output_dir / f"metal_manifest_{mode}.csv"
    if metal_manifest.exists():
        print(f"metal_manifest_{mode}.csv 已存在，跳过重新检测。")
        return metal_manifest

    output_dir.mkdir(parents=True, exist_ok=True)
    images_dir = data_dir / "images"

    with open(src_manifest, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    print(f"共 {len(rows)} 张切片，正在检测金属（从骨窗通道）...")

    cls_names = BINARY_CLASSES if mode == "binary" else SEVERITY_CLASSES
    metal_rows: list[dict] = []
    skipped = 0

    for r in tqdm(rows, desc="金属检测"):
        png_path = images_dir / r["slice_name"]
        if not png_path.exists():
            skipped += 1
            continue

        coverage = detect_metal_from_png(png_path)
        lbl      = coverage_to_label(coverage, mode)

        metal_rows.append({
            "slice_png":    str(png_path),
            "label":        lbl,
            "patient_id":   r["patient_id"],
            "slice_index":  r["slice_index"],
            "coverage_pct": round(coverage, 4),
            "fold":         r["fold"],            # 复用原始 fold 划分（按病人 KFold）
        })

    if skipped:
        print(f"⚠  跳过 {skipped} 张找不到的 PNG（可能 tar 包解压不完整）")

    # 分布统计
    counts = np.bincount([r["label"] for r in metal_rows], minlength=len(cls_names))
    print(f"\n切片标签分布（mode={mode}）:")
    for cls, cnt in zip(cls_names, counts):
        ratio = cnt / len(metal_rows) * 100 if metal_rows else 0
        print(f"  {cls:<30s}: {cnt:>6}  ({ratio:.1f}%)")

    # 数据量警告
    n_cases = len({r["patient_id"] for r in metal_rows})
    n_metal = sum(1 for r in metal_rows if r["label"] > 0)
    print(f"\n总计: {len(metal_rows)} 切片，{n_cases} 个病例，{n_metal} 张有金属")
    if n_metal < 200:
        print(
            "⚠  有金属切片 < 200，过拟合风险高！\n"
            "   建议：用更多 CQ500 病例重新运行 preprocess_cq500.py"
        )

    fields = ["slice_png", "label", "patient_id", "slice_index", "coverage_pct", "fold"]
    with open(metal_manifest, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(metal_rows)

    print(f"metal_manifest_{mode}.csv 已生成: {metal_manifest}")
    return metal_manifest


# ─────────────────────────────────────────────────────────────────────────────
# 模型 & 损失
# ─────────────────────────────────────────────────────────────────────────────

class FocalLoss(nn.Module):
    """FocalLoss + Label Smoothing（缓解噪声标注 & 类别不平衡）。"""
    def __init__(self, gamma: float = 2.0,
                 weight: Optional[torch.Tensor] = None,
                 smoothing: float = 0.1):
        super().__init__()
        self.gamma     = gamma
        self.weight    = weight
        self.smoothing = smoothing

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        n = logits.size(1)
        with torch.no_grad():
            st = torch.full_like(logits, self.smoothing / max(n - 1, 1))
            st.scatter_(1, targets.unsqueeze(1), 1.0 - self.smoothing)
        lp  = torch.log_softmax(logits, dim=1)
        ce  = -(st * lp).sum(dim=1)
        pt  = torch.exp(-nn.functional.cross_entropy(logits, targets, reduction="none"))
        return ((1 - pt) ** self.gamma * ce).mean()


_RADIMGNET_GDRIVE_ID = "1CqSsaXBBriCQBZMWvAlZdqNDiMpRcLtZ"
_RADIMGNET_CACHE     = Path.home() / ".cache" / "radimgnet" / "efficientnet_b7.pth"


def _try_radimgnet() -> Optional[Path]:
    if _RADIMGNET_CACHE.exists():
        return _RADIMGNET_CACHE
    try:
        import gdown
        _RADIMGNET_CACHE.parent.mkdir(parents=True, exist_ok=True)
        gdown.download(id=_RADIMGNET_GDRIVE_ID, output=str(_RADIMGNET_CACHE), quiet=False)
        return _RADIMGNET_CACHE
    except Exception as e:
        print(f"RadImageNet 下载失败，使用 ImageNet: {e}")
        return None


def build_model(n_classes: int) -> nn.Module:
    model = timm.create_model("efficientnet_b3", pretrained=True,
                               num_classes=n_classes, drop_rate=0.3)
    rad = _try_radimgnet()
    if rad:
        ckpt = torch.load(rad, map_location="cpu")
        model.load_state_dict(ckpt, strict=False)
        print("RadImageNet 权重加载完成")
    return model


# ─────────────────────────────────────────────────────────────────────────────
# 数据变换（与 train_classifier.py 一致，复用已处理好的 PNG）
# ─────────────────────────────────────────────────────────────────────────────

def get_transforms(is_train: bool) -> Compose:
    """
    PNG 已经是 3 通道（脑窗/血窗/骨窗），与 train_classifier.py 使用相同变换。
    金属伪影呈放射状 → 使用稍大的旋转增强。
    """
    base = [
        LoadImaged(keys=["image"], image_only=True),
        EnsureChannelFirstd(keys=["image"]),
        Resized(keys=["image"], spatial_size=(512, 512)),
        NormalizeIntensityd(keys=["image"], nonzero=True, channel_wise=True),
        EnsureTyped(keys=["image"], dtype=torch.float32),
    ]
    if is_train:
        aug = [
            RandFlipd(keys=["image"], prob=0.5, spatial_axis=1),
            # 金属伪影呈放射状，大角度旋转增强多样性
            RandRotate90d(keys=["image"], prob=0.4, max_k=3),
            RandAffined(
                keys=["image"], prob=0.5,
                rotate_range=(0.35, 0.35),
                scale_range=(0.1, 0.1),
                translate_range=(20, 20),
                mode="bilinear", padding_mode="zeros",
            ),
        ]
        return Compose(base + aug)
    return Compose(base)


def mixup(x: torch.Tensor, y_onehot: torch.Tensor,
          alpha: float = 0.2) -> tuple[torch.Tensor, torch.Tensor]:
    if alpha <= 0:
        return x, y_onehot
    lam = float(np.random.beta(alpha, alpha))
    idx = torch.randperm(x.size(0), device=x.device)
    return lam * x + (1 - lam) * x[idx], lam * y_onehot + (1 - lam) * y_onehot[idx]


# ─────────────────────────────────────────────────────────────────────────────
# 训练 & 评估
# ─────────────────────────────────────────────────────────────────────────────

def load_split(manifest: Path, fold: int, is_train: bool) -> list[dict]:
    with open(manifest, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    result = []
    for r in rows:
        in_val = int(r["fold"]) == fold
        if (is_train and not in_val) or (not is_train and in_val):
            result.append({"image": r["slice_png"], "label": int(r["label"])})
    return result


def class_weights(items: list[dict], n: int) -> torch.Tensor:
    c = np.bincount([it["label"] for it in items], minlength=n).astype(float)
    w = 1.0 / (c + 1e-6)
    return torch.tensor(w / w.sum() * n, dtype=torch.float32)


def train_one_epoch(model, loader, optimizer, criterion, n_classes,
                    device, mixup_alpha: float) -> float:
    """
    修复：使用 FocalLoss（含类别权重）+ MixUp 两者结合。
    具体做法：FocalLoss 在 hard labels 上计算 focal 因子（pt），
    再与 MixUp soft-label CE 组合，近似保留类别权重的效果。
    """
    model.train()
    total = 0.0
    for batch in tqdm(loader, desc="  train", leave=False):
        x = batch["image"].to(device)
        y = batch["label"].to(device)

        if mixup_alpha > 0:
            # MixUp：soft-label CE + 类别权重
            y_oh = nn.functional.one_hot(y, n_classes).float()
            x_m, y_oh_m = mixup(x, y_oh, mixup_alpha)
            logits   = model(x_m)
            log_prob = torch.log_softmax(logits, dim=1)
            # 按原始 hard label 获取 focal 权重（近似）
            with torch.no_grad():
                pt_approx = torch.exp(
                    -nn.functional.cross_entropy(logits.detach(), y, reduction="none"))
                focal_w = (1 - pt_approx) ** criterion.gamma
                if criterion.weight is not None:
                    cls_w = criterion.weight[y]
                    focal_w = focal_w * cls_w
            loss = -(y_oh_m * log_prob).sum(dim=1) * focal_w
            loss = loss.mean()
        else:
            # 不用 MixUp：直接 FocalLoss（更准确）
            logits = model(x)
            loss   = criterion(logits, y)

        optimizer.zero_grad(); loss.backward(); optimizer.step()
        total += loss.item()
    return total / len(loader)


@torch.no_grad()
def evaluate(model, loader, criterion, device):
    model.eval()
    all_preds, all_labels, total = [], [], 0.0
    for batch in tqdm(loader, desc="  val  ", leave=False):
        x = batch["image"].to(device)
        y = batch["label"].to(device)
        logits = model(x)
        total += criterion(logits, y).item()
        all_preds.extend(logits.argmax(1).cpu().numpy().tolist())
        all_labels.extend(batch["label"].numpy().tolist())
    p, l = np.array(all_preds), np.array(all_labels)
    acc          = np.mean(p == l)
    art          = l > 0
    recall_metal = np.mean(p[art] > 0) if art.any() else 0.0
    # F1 of metal class (balances precision and recall)
    tp = int(((p > 0) & (l > 0)).sum())
    fp = int(((p > 0) & (l == 0)).sum())
    fn = int(((p == 0) & (l > 0)).sum())
    f1_metal = 2 * tp / (2 * tp + fp + fn + 1e-8)
    return total / len(loader), acc, recall_metal, f1_metal, p, l


def save_curves(history: list[dict], output_dir: Path, tag: str) -> None:
    """保存训练曲线为 PNG 图片，方便后续可视化。"""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        epochs      = [h["epoch"]        for h in history]
        train_loss  = [h["train_loss"]   for h in history]
        val_loss    = [h.get("val_loss", None) for h in history]
        acc         = [h.get("acc",         None) for h in history]
        recall_metal= [h.get("recall_metal",None) for h in history]
        f1_metal    = [h.get("f1_metal",    None) for h in history]

        fig, axes = plt.subplots(1, 3, figsize=(15, 4))
        fig.suptitle(f"Training Curves  [{tag}]", fontsize=13)

        # Loss
        axes[0].plot(epochs, train_loss, label="train_loss", color="#2196F3")
        if any(v is not None for v in val_loss):
            axes[0].plot(epochs, val_loss,  label="val_loss",   color="#FF7043")
        axes[0].set_title("Loss"); axes[0].legend(); axes[0].grid(True, alpha=0.3)

        # Accuracy
        if any(v is not None for v in acc):
            axes[1].plot(epochs, acc, label="val_acc", color="#4CAF50")
        axes[1].set_title("Accuracy"); axes[1].legend(); axes[1].set_ylim(0, 1)
        axes[1].grid(True, alpha=0.3)

        # Recall & F1 of metal class
        if any(v is not None for v in recall_metal):
            axes[2].plot(epochs, recall_metal, label="recall_metal", color="#F44336")
        if any(v is not None for v in f1_metal):
            axes[2].plot(epochs, f1_metal,     label="f1_metal",     color="#9C27B0")
        axes[2].set_title("Metal Detection"); axes[2].legend(); axes[2].set_ylim(0, 1)
        axes[2].grid(True, alpha=0.3)

        plt.tight_layout()
        out_png = output_dir / f"curves_{tag}.png"
        plt.savefig(str(out_png), dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"训练曲线已保存: {out_png}")
    except ImportError:
        print("matplotlib 未安装，跳过曲线保存（pip install matplotlib）")


# ─────────────────────────────────────────────────────────────────────────────
# 主函数
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="金属伪影分类（基于 preprocess_cq500 输出）")
    parser.add_argument("--data",     required=True,
                        help="preprocess_cq500 输出目录（含 manifest.csv 和 images/）")
    parser.add_argument("--output",   required=True,  help="模型输出目录")
    parser.add_argument("--mode",     default="binary", choices=["binary", "severity"])
    parser.add_argument("--fold",     type=int,   default=0)
    parser.add_argument("--epochs",   type=int,   default=40)
    parser.add_argument("--batch",    type=int,   default=32)
    parser.add_argument("--lr",       type=float, default=3e-5)
    parser.add_argument("--workers",  type=int,   default=8)
    parser.add_argument("--patience", type=int,   default=10)
    parser.add_argument("--mixup",    type=float, default=0.2)
    args = parser.parse_args()

    data_dir   = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    cls_names  = BINARY_CLASSES if args.mode == "binary" else SEVERITY_CLASSES
    n_cls      = len(cls_names)

    # ── 阶段1：生成金属标签 ───────────────────────────────────────────────────
    print("=" * 60)
    print("阶段1：从骨窗 PNG 检测金属标签")
    print("=" * 60)
    manifest = build_metal_manifest(data_dir, output_dir, args.mode)

    # ── 阶段2：训练 ────────────────────────────────────────────────────────
    print("=" * 60)
    print(f"阶段2：训练  mode={args.mode}  fold={args.fold}")
    print("=" * 60)

    device      = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_items = load_split(manifest, args.fold, is_train=True)
    val_items   = load_split(manifest, args.fold, is_train=False)
    print(f"训练: {len(train_items)} | 验证: {len(val_items)} | 设备: {device}")

    train_ds = Dataset(data=train_items, transform=get_transforms(True))
    val_ds   = Dataset(data=val_items,   transform=get_transforms(False))
    train_dl = DataLoader(train_ds, batch_size=args.batch, shuffle=True,
                          num_workers=args.workers, pin_memory=True)
    val_dl   = DataLoader(val_ds,   batch_size=args.batch, shuffle=False,
                          num_workers=args.workers, pin_memory=True)

    model     = build_model(n_cls).to(device)
    weights   = class_weights(train_items, n_cls).to(device)
    criterion = FocalLoss(gamma=2.0, weight=weights, smoothing=0.1)
    optimizer = AdamW(model.parameters(), lr=args.lr, weight_decay=1e-3)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-7)

    tag = f"{args.mode}_fold{args.fold}"
    # ── 早停指标：recall_metal（不用 acc，类别不平衡时 acc 不可靠）──────────
    best_recall, no_imp, history = 0.0, 0, []

    for epoch in range(1, args.epochs + 1):
        tr_loss = train_one_epoch(model, train_dl, optimizer, criterion,
                                  n_cls, device, args.mixup)
        scheduler.step()

        # ── 每轮都保存 last.pt（训练中断可恢复）──────────────────────────────
        torch.save({
            "epoch": epoch,
            "model": model.state_dict(),
            "optimizer": optimizer.state_dict(),
            "scheduler": scheduler.state_dict(),
        }, output_dir / f"last_{tag}.pt")

        row: dict = {"epoch": epoch, "train_loss": round(tr_loss, 6)}

        if val_items:
            va_loss, acc, rec, f1, preds, labels = evaluate(
                model, val_dl, criterion, device)
            lr_now = optimizer.param_groups[0]["lr"]
            print(f"Epoch {epoch:03d} | train={tr_loss:.4f} | val={va_loss:.4f} "
                  f"| acc={acc:.4f} | recall_metal={rec:.4f} | f1_metal={f1:.4f} "
                  f"| lr={lr_now:.2e}")
            row.update(val_loss=round(va_loss, 6), acc=round(acc, 4),
                       recall_metal=round(rec, 4), f1_metal=round(f1, 4), lr=lr_now)

            # ── 早停：以 recall_metal 为准（漏诊 > 误诊，recall 优先）──────
            if rec > best_recall:
                best_recall, no_imp = rec, 0
                torch.save(model.state_dict(), output_dir / f"best_{tag}.pt")
                print(f"  ✓ best_recall_metal={best_recall:.4f}")
            else:
                no_imp += 1
                if no_imp >= args.patience:
                    print(f"Early stopping（recall_metal 连续 {args.patience} 轮无提升）")
                    break
        else:
            print(f"Epoch {epoch:03d} | train={tr_loss:.4f}")
            torch.save(model.state_dict(), output_dir / f"best_{tag}.pt")

        # ── history 每轮都写（无论是否有验证集）──────────────────────────────
        history.append(row)
        (output_dir / f"history_{tag}.json").write_text(
            json.dumps(history, indent=2), encoding="utf-8")

    # ── 阶段3：导出 ONNX ──────────────────────────────────────────────────
    model.load_state_dict(torch.load(output_dir / f"best_{tag}.pt", map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, 512, 512).to(device)
    onnx_path = output_dir / f"metal_classifier_{args.mode}.onnx"
    torch.onnx.export(model, dummy, str(onnx_path),
                      input_names=["input"], output_names=["logits"],
                      dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
                      opset_version=17)
    print(f"\nONNX → {onnx_path}")

    if val_items:
        _, _, _, _, fp, fl = evaluate(model, val_dl, criterion, device)
        report = classification_report(fl, fp, target_names=cls_names, digits=4)
        print("\n分类报告:\n", report)
        (output_dir / f"report_{tag}.txt").write_text(report, encoding="utf-8")

    # ── 保存训练曲线 PNG ───────────────────────────────────────────────────
    save_curves(history, output_dir, tag)

    print(f"\n完成  best_recall_metal={best_recall:.4f}")
    print(f"输出目录: {output_dir}")
    print(f"  best_{tag}.pt    ← 验证集 recall_metal 最高时的权重")
    print(f"  last_{tag}.pt    ← 最后一轮的完整 checkpoint（含 optimizer）")
    print(f"  history_{tag}.json  ← 每轮指标，可用于绘图")
    print(f"  curves_{tag}.png    ← 训练曲线图")


if __name__ == "__main__":
    main()
