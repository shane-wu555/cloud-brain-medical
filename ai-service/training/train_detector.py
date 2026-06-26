"""
阶段③：YOLOv8-small 病灶检测框训练
将 manifest.csv + yolo_labels/ 组织为 YOLO 数据集格式，然后训练

用法:
    python training/train_detector.py \
        --data   /data/ct500_processed \
        --output /models/detector \
        --fold   0 \
        --epochs 100
"""

import argparse
import csv
import json
import os
import shutil
from pathlib import Path

import yaml
from tqdm import tqdm


CLASSES = ["hemorrhage", "ischemia"]  # 检测目标（不含 normal）


def build_yolo_dataset(data_dir: Path, output_dir: Path, fold: int) -> Path:
    """
    将 manifest.csv 中有 bbox 的切片复制到 YOLO 目录结构:
        yolo_dataset/
            images/train/  images/val/
            labels/train/  labels/val/
    """
    manifest_path = data_dir / "manifest.csv"
    yolo_src_labels = data_dir / "yolo_labels"

    yolo_dir = output_dir / "yolo_dataset"
    for split in ["train", "val"]:
        (yolo_dir / "images" / split).mkdir(parents=True, exist_ok=True)
        (yolo_dir / "labels" / split).mkdir(parents=True, exist_ok=True)

    with open(manifest_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    # 只处理有 bbox 标注的切片
    bbox_rows = [r for r in rows if r["has_lesion"] == "True"]
    print(f"有 bbox 标注的切片: {len(bbox_rows)}")

    for r in tqdm(bbox_rows, desc="构建 YOLO 数据集"):
        split = "val" if int(r["fold"]) == fold else "train"
        img_src  = data_dir / "images" / r["slice_name"]
        lbl_src  = yolo_src_labels / r["slice_name"].replace(".png", ".txt")

        img_dst = yolo_dir / "images" / split / r["slice_name"]
        lbl_dst = yolo_dir / "labels" / split / r["slice_name"].replace(".png", ".txt")

        if img_src.exists():
            shutil.copy2(img_src, img_dst)
        if lbl_src.exists():
            shutil.copy2(lbl_src, lbl_dst)
        else:
            # 无 label 文件时创建空文件（表示背景切片，用于负样本）
            lbl_dst.write_text("")

    # 生成 data.yaml
    data_yaml = {
        "path": str(yolo_dir.resolve()),
        "train": "images/train",
        "val": "images/val",
        "nc": len(CLASSES),
        "names": CLASSES,
    }
    yaml_path = yolo_dir / "data.yaml"
    yaml_path.write_text(yaml.dump(data_yaml, allow_unicode=True), encoding="utf-8")
    print(f"YOLO 数据集准备完毕: {yolo_dir}")
    return yaml_path


def main() -> None:
    parser = argparse.ArgumentParser(description="YOLOv8 CT 检测训练")
    parser.add_argument("--data",    required=True)
    parser.add_argument("--output",  required=True)
    parser.add_argument("--fold",    type=int, default=0)
    parser.add_argument("--epochs",  type=int, default=100)
    parser.add_argument("--batch",   type=int, default=16)
    parser.add_argument("--imgsz",   type=int, default=512)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--device",  type=str, default="",
                        help="'' → 自动, '0' → GPU0, 'cpu' → CPU")
    args = parser.parse_args()

    data_dir   = Path(args.data)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 构建 YOLO 格式数据集
    yaml_path = build_yolo_dataset(data_dir, output_dir, fold=args.fold)

    # 训练（需要 ultralytics 已安装）
    from ultralytics import YOLO

    model = YOLO("yolov8s.pt")  # 下载预训练权重
    results = model.train(
        data=str(yaml_path),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        workers=args.workers,
        device=args.device or ("0" if __import__("torch").cuda.is_available() else "cpu"),
        project=str(output_dir),
        name="ct_detector",
        exist_ok=True,
        # 医学影像适配：关闭 mosaic，减少几何失真
        mosaic=0.0,
        degrees=10.0,
        translate=0.05,
        scale=0.1,
        fliplr=0.5,
        # 置信度阈值适度降低（宁可多报，由医生确认）
        conf=0.3,
        iou=0.5,
        # 输出
        save=True,
        plots=True,
    )

    # 导出 ONNX
    best_pt = output_dir / "ct_detector" / "weights" / "best.pt"
    if best_pt.exists():
        export_model = YOLO(str(best_pt))
        export_model.export(format="onnx", imgsz=args.imgsz, opset=17)
        onnx_src = best_pt.with_suffix(".onnx")
        onnx_dst = output_dir / "detector.onnx"
        if onnx_src.exists():
            shutil.copy2(onnx_src, onnx_dst)
            print(f"检测器 ONNX 已保存: {onnx_dst}")

    print(f"训练完成，结果目录: {output_dir}")


if __name__ == "__main__":
    main()
