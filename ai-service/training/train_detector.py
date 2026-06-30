"""
阶段③：YOLOv8-small 病灶检测框训练
====================================
⚠  数据来源：必须是 preprocess_ct500.py 的输出，不能用 preprocess_cq500.py 的 CQ500 数据
   原因：CQ500 数据没有 bbox 标注（只有切片级 label），CT500 才有 bbox。

   正确流程：
     1. python training/preprocess_ct500.py --input /data/CT500 --output /data/ct500_processed ...
     2. python training/train_detector.py   --data  /data/ct500_processed ...

修复清单（对比旧版）：
  ① conf=0.3 移出 model.train()（它是推理参数，训练时无效）
  ② 正常切片（负样本）加入训练集（原版只放有病灶切片，导致模型乱报）
  ③ 关闭对 CT 无意义的颜色增强（hsv_h / hsv_s）
  ④ 加入 patience 早停参数
  ⑤ 统计负样本比例并给出警告

用法:
    python training/train_detector.py \\
        --data   /root/autodl-tmp/ct500_processed \\
        --output /root/autodl-tmp/models/detector \\
        --fold   0 \\
        --epochs 100
"""

import argparse
import csv
import random
import shutil
from pathlib import Path

import torch
import yaml
from tqdm import tqdm


CLASSES = ["hemorrhage", "ischemia"]   # class 0 = hemorrhage, class 1 = ischemia

# 每个正样本病例，采样多少张负样本切片加入训练
# 比值过高会让模型太保守，过低会乱报
NEG_POS_RATIO = 2   # 每1张有病灶切片配2张正常切片


def build_yolo_dataset(data_dir: Path, output_dir: Path, fold: int) -> Path:
    """
    将 ct500 manifest + yolo_labels 组织为 YOLO 目录结构:
        yolo_dataset/
            images/train/  images/val/
            labels/train/  labels/val/

    修复：同时包含正样本（有病灶）和负样本（无病灶）切片。
    """
    manifest_path = data_dir / "manifest.csv"
    yolo_src      = data_dir / "yolo_labels"

    if not manifest_path.exists():
        raise FileNotFoundError(
            f"找不到 manifest.csv: {manifest_path}\n"
            f"请先运行 preprocess_ct500.py（不是 preprocess_cq500.py！）"
        )
    if not yolo_src.exists():
        raise FileNotFoundError(
            f"找不到 yolo_labels/: {yolo_src}\n"
            f"preprocess_ct500.py 应该会生成此目录"
        )

    yolo_dir = output_dir / "yolo_dataset"
    for split in ["train", "val"]:
        (yolo_dir / "images" / split).mkdir(parents=True, exist_ok=True)
        (yolo_dir / "labels" / split).mkdir(parents=True, exist_ok=True)

    with open(manifest_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    # ── 分正负样本 ──────────────────────────────────────────────────────────
    pos_rows = [r for r in rows if r.get("has_lesion", "False") == "True"]
    neg_rows = [r for r in rows if r.get("has_lesion", "False") != "True"]

    if not pos_rows:
        raise RuntimeError(
            "manifest.csv 中没有任何有 bbox 标注的切片（has_lesion=True）。\n"
            "请确认数据来自 preprocess_ct500.py，且 labels.json 含有 bboxes 字段。"
        )

    # 采样负样本（按 NEG_POS_RATIO）
    n_neg = min(len(neg_rows), len(pos_rows) * NEG_POS_RATIO)
    rng = random.Random(42)
    neg_sampled = rng.sample(neg_rows, n_neg)

    all_rows = pos_rows + neg_sampled
    print(f"正样本（有病灶）: {len(pos_rows)}")
    print(f"负样本（无病灶）: {n_neg}（从 {len(neg_rows)} 张中采样，比例 1:{NEG_POS_RATIO}）")
    print(f"总切片数: {len(all_rows)}")

    # ── 按 fold 划分 train/val ──────────────────────────────────────────────
    for r in tqdm(all_rows, desc="构建 YOLO 数据集"):
        split    = "val" if int(r["fold"]) == fold else "train"
        img_src  = data_dir / "images" / r["slice_name"]
        lbl_src  = yolo_src / r["slice_name"].replace(".png", ".txt")
        img_dst  = yolo_dir / "images" / split / r["slice_name"]
        lbl_dst  = yolo_dir / "labels" / split / r["slice_name"].replace(".png", ".txt")

        if img_src.exists():
            shutil.copy2(img_src, img_dst)

        if lbl_src.exists():
            shutil.copy2(lbl_src, lbl_dst)
        else:
            # 无 label → 负样本，创建空 txt（YOLO 标准做法）
            lbl_dst.write_text("")

    # ── 统计 val 集 ─────────────────────────────────────────────────────────
    val_pos = sum(1 for r in all_rows if int(r["fold"]) == fold
                  and r.get("has_lesion") == "True")
    val_neg = sum(1 for r in all_rows if int(r["fold"]) == fold
                  and r.get("has_lesion") != "True")
    print(f"验证集：{val_pos} 正 + {val_neg} 负")

    # ── data.yaml ───────────────────────────────────────────────────────────
    data_yaml = {
        "path":  str(yolo_dir.resolve()),
        "train": "images/train",
        "val":   "images/val",
        "nc":    len(CLASSES),
        "names": CLASSES,
    }
    yaml_path = yolo_dir / "data.yaml"
    yaml_path.write_text(yaml.dump(data_yaml, allow_unicode=True), encoding="utf-8")
    print(f"YOLO 数据集准备完毕: {yolo_dir}")
    return yaml_path


def main() -> None:
    parser = argparse.ArgumentParser(description="YOLOv8 CT 病灶检测训练")
    parser.add_argument("--data",     required=True,
                        help="preprocess_ct500.py 的输出目录")
    parser.add_argument("--output",   required=True)
    parser.add_argument("--fold",     type=int,   default=0)
    parser.add_argument("--epochs",   type=int,   default=100)
    parser.add_argument("--batch",    type=int,   default=16)
    parser.add_argument("--imgsz",    type=int,   default=512)
    parser.add_argument("--workers",  type=int,   default=4)
    parser.add_argument("--patience", type=int,   default=20,
                        help="早停轮数（验证 mAP 不提升）")
    parser.add_argument("--device",   type=str,   default="",
                        help="'' 自动 / '0' GPU0 / 'cpu'")
    args = parser.parse_args()

    data_dir   = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    device = args.device or ("0" if torch.cuda.is_available() else "cpu")

    # ── 构建数据集 ───────────────────────────────────────────────────────────
    yaml_path = build_yolo_dataset(data_dir, output_dir, fold=args.fold)

    # ── 训练 ─────────────────────────────────────────────────────────────────
    from ultralytics import YOLO

    model = YOLO("yolov8s.pt")
    model.train(
        data    = str(yaml_path),
        epochs  = args.epochs,
        imgsz   = args.imgsz,
        batch   = args.batch,
        workers = args.workers,
        device  = device,
        project = str(output_dir),
        name    = "ct_detector",
        exist_ok= True,
        patience= args.patience,       # ✅ 早停

        # ── CT 图像增强（关闭无意义的颜色增强）─────────────────────────────
        # CT 是灰度图，hue/saturation 增强会破坏三通道窗口的物理含义
        hsv_h   = 0.0,    # ✅ 关闭色调增强（原默认 0.015）
        hsv_s   = 0.0,    # ✅ 关闭饱和度增强（原默认 0.7）
        hsv_v   = 0.15,   # 保留少量亮度变化（模拟不同扫描仪）

        # ── 几何增强（医学影像适配）─────────────────────────────────────────
        mosaic   = 0.0,   # 关闭拼接（CT 切片不应拼接）
        degrees  = 10.0,  # 小幅旋转
        translate= 0.05,
        scale    = 0.1,
        fliplr   = 0.5,
        flipud   = 0.0,   # CT 上下一般不翻转

        # ── 输出 ────────────────────────────────────────────────────────────
        save  = True,
        plots = True,

        # ⚠  conf 是推理参数，不能放在 train() 里（旧版 bug，已移除）
        # 推理时用：model.predict(..., conf=0.3)
    )

    # ── 导出 ONNX ────────────────────────────────────────────────────────────
    best_pt = output_dir / "ct_detector" / "weights" / "best.pt"
    if best_pt.exists():
        export_model = YOLO(str(best_pt))
        export_model.export(format="onnx", imgsz=args.imgsz, opset=17)
        # Ultralytics 将 ONNX 保存在 weights/ 同级
        onnx_src = best_pt.with_suffix(".onnx")
        onnx_dst = output_dir / "detector.onnx"
        if onnx_src.exists():
            shutil.copy2(onnx_src, onnx_dst)
            print(f"检测器 ONNX 已保存: {onnx_dst}")
        else:
            print(f"⚠  未找到 ONNX 文件: {onnx_src}")
            print("   可手动导出：yolo export model=best.pt format=onnx")
    else:
        print(f"⚠  未找到 best.pt: {best_pt}")

    print(f"\n训练完成，结果目录: {output_dir}/ct_detector")
    print("推理置信度阈值在 predict 时设置，例如:")
    print(f"  model.predict(source=img, conf=0.3, iou=0.5)")


if __name__ == "__main__":
    main()
