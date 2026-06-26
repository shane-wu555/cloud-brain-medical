"""
阶段①：CT500 数据集预处理
将 DICOM 体积 → PNG 切片 + CSV 标注清单
支持本地目录 和 MinIO 路径（minio://bucket/prefix）两种输入格式

用法（本地）:
    python training/preprocess_ct500.py \
        --input  /data/CT500 \
        --output /data/ct500_processed \
        --labels /data/CT500/labels.json

用法（MinIO 团队共享）:
    python training/preprocess_ct500.py \
        --input  minio://training-data/CT500 \
        --output minio://training-output/ct500_processed \
        --labels minio://training-data/CT500/labels.json
"""

import argparse
import json
import csv
import os
import tempfile
from pathlib import Path

import cv2
import numpy as np
import pydicom
import SimpleITK as sitk
from sklearn.model_selection import StratifiedKFold
from tqdm import tqdm


LABEL_MAP = {"normal": 0, "hemorrhage": 1, "ischemia": 2}


def apply_windows(hu: np.ndarray) -> np.ndarray:
    """HU值 → 3通道图像（脑窗/血窗/骨窗）"""
    def win(arr, w, l):
        lo, hi = l - w / 2, l + w / 2
        return np.clip((arr - lo) / (hi - lo) * 255, 0, 255).astype(np.uint8)

    brain = win(hu, w=80,   l=40)    # 脑组织
    blood = win(hu, w=175,  l=75)    # 出血检测
    bone  = win(hu, w=2500, l=480)   # 颅骨

    return np.stack([brain, blood, bone], axis=-1)  # (H, W, 3)


def load_dicom_series(patient_dir: Path) -> np.ndarray:
    """读取一个病人目录下的 DICOM 序列，返回 (N, H, W) HU 数组"""
    reader = sitk.ImageSeriesReader()
    dcm_files = reader.GetGDCMSeriesFileNames(str(patient_dir))
    if not dcm_files:
        raise ValueError(f"目录中没有 DICOM 文件: {patient_dir}")
    reader.SetFileNames(dcm_files)
    img = reader.Execute()
    arr = sitk.GetArrayFromImage(img).astype(np.float32)  # (Z, Y, X)
    slope = img.GetMetaData("0028|1053") if img.HasMetaDataKey("0028|1053") else "1"
    intercept = img.GetMetaData("0028|1052") if img.HasMetaDataKey("0028|1052") else "-1024"
    arr = arr * float(slope) + float(intercept)
    return arr


def load_nifti(nii_path: Path) -> np.ndarray:
    """读取 NIfTI 文件，返回 (N, H, W) HU 数组"""
    img = sitk.ReadImage(str(nii_path))
    arr = sitk.GetArrayFromImage(img).astype(np.float32)
    return arr


def process_volume(patient_id: str, hu_volume: np.ndarray,
                   label: str, bboxes: list[dict],
                   out_dir: Path) -> list[dict]:
    """处理单个患者的 CT 体积，保存切片 PNG，返回标注记录列表"""
    records = []
    n_slices = hu_volume.shape[0]

    # 只保留有效切片（排除全黑/床板切片）
    valid_mask = hu_volume.mean(axis=(1, 2)) > -900

    for z in range(n_slices):
        if not valid_mask[z]:
            continue

        rgb = apply_windows(hu_volume[z])
        rgb_resized = cv2.resize(rgb, (512, 512))

        slice_name = f"{patient_id}_z{z:03d}.png"
        save_path = out_dir / "images" / slice_name
        cv2.imwrite(str(save_path), cv2.cvtColor(rgb_resized, cv2.COLOR_RGB2BGR))

        # 找到属于这个切片的 bbox（如果有）
        slice_bboxes = [b for b in bboxes if b.get("sliceIndex") == z]

        records.append({
            "patient_id": patient_id,
            "slice_name": slice_name,
            "slice_index": z,
            "label": label,
            "label_id": LABEL_MAP.get(label, 0),
            "has_lesion": len(slice_bboxes) > 0,
            "bboxes": json.dumps(slice_bboxes),
        })

    return records


def build_yolo_labels(records: list[dict], out_dir: Path) -> None:
    """将 bbox 标注转为 YOLO 格式 .txt 文件"""
    yolo_dir = out_dir / "yolo_labels"
    yolo_dir.mkdir(exist_ok=True)

    for rec in records:
        label_path = yolo_dir / rec["slice_name"].replace(".png", ".txt")
        bboxes = json.loads(rec["bboxes"])
        lines = []
        for b in bboxes:
            # 期望 bbox 格式: {x1, y1, x2, y2, class_id} (像素坐标, 原始512x512)
            x1, y1, x2, y2 = b["x1"], b["y1"], b["x2"], b["y2"]
            cls = b.get("class_id", LABEL_MAP.get(b.get("label", "hemorrhage"), 1))
            xc = (x1 + x2) / 2 / 512
            yc = (y1 + y2) / 2 / 512
            w  = (x2 - x1) / 512
            h  = (y2 - y1) / 512
            lines.append(f"{cls} {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}")
        label_path.write_text("\n".join(lines))


def split_folds(records: list[dict], n_folds: int = 5) -> list[dict]:
    """按病人 ID 划分 5-fold，避免同一病人跨 fold 泄漏"""
    patients = sorted({r["patient_id"] for r in records})
    labels_per_patient = {
        p: records[[r["patient_id"] for r in records].index(p)]["label_id"]
        for p in patients
    }
    patient_arr = np.array(patients)
    label_arr   = np.array([labels_per_patient[p] for p in patients])

    kf = StratifiedKFold(n_splits=n_folds, shuffle=True, random_state=42)
    fold_map: dict[str, int] = {}
    for fold_idx, (_, val_idx) in enumerate(kf.split(patient_arr, label_arr)):
        for p in patient_arr[val_idx]:
            fold_map[p] = fold_idx

    for r in records:
        r["fold"] = fold_map[r["patient_id"]]
    return records


def _parse_minio_url(url: str) -> tuple[str, str]:
    """minio://bucket/prefix → (bucket, prefix)"""
    path = url[len("minio://"):]
    bucket, _, prefix = path.partition("/")
    return bucket, prefix


def main() -> None:
    parser = argparse.ArgumentParser(description="CT500 预处理")
    parser.add_argument("--input",  required=True,
                        help="CT500 根目录，支持本地路径或 minio://bucket/prefix")
    parser.add_argument("--output", required=True,
                        help="输出目录，支持本地路径或 minio://bucket/prefix")
    parser.add_argument("--labels", required=True,
                        help="标注 JSON，支持本地路径或 minio://bucket/object")
    parser.add_argument("--folds",  type=int, default=5)
    args = parser.parse_args()

    use_minio = args.input.startswith("minio://")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)

        if use_minio:
            from minio_io import client, download_dir, upload_dir, upload_file
            mc = client()

            # 下载原始数据到临时目录
            in_bucket, in_prefix = _parse_minio_url(args.input)
            lbl_bucket, lbl_object = _parse_minio_url(args.labels)
            print(f"从 MinIO 下载数据集: {args.input}")
            local_input = tmp_path / "input"
            download_dir(mc, in_bucket, in_prefix, local_input)

            lbl_local = tmp_path / "labels.json"
            mc.fget_object(lbl_bucket, lbl_object, str(lbl_local))

            local_output = tmp_path / "output"
        else:
            local_input  = Path(args.input)
            lbl_local    = Path(args.labels)
            local_output = Path(args.output)

        (local_output / "images").mkdir(parents=True, exist_ok=True)

        with open(lbl_local, encoding="utf-8") as f:
            annotations: list[dict] = json.load(f)

        ann_map = {a["patientId"]: a for a in annotations}
        all_records: list[dict] = []

        patient_dirs = sorted(local_input.iterdir())
        for patient_dir in tqdm(patient_dirs, desc="处理患者"):
            if not patient_dir.is_dir():
                continue
            patient_id = patient_dir.name
            ann = ann_map.get(patient_id, {"label": "normal", "bboxes": []})

            try:
                nii_files = list(patient_dir.glob("*.nii.gz")) + list(patient_dir.glob("*.nii"))
                hu_volume = load_nifti(nii_files[0]) if nii_files else load_dicom_series(patient_dir)
            except Exception as e:
                print(f"[跳过] {patient_id}: {e}")
                continue

            records = process_volume(
                patient_id=patient_id,
                hu_volume=hu_volume,
                label=ann["label"],
                bboxes=ann.get("bboxes", []),
                out_dir=local_output,
            )
            all_records.extend(records)

        all_records = split_folds(all_records, n_folds=args.folds)
        build_yolo_labels(all_records, local_output)

        manifest_path = local_output / "manifest.csv"
        with open(manifest_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=all_records[0].keys())
            writer.writeheader()
            writer.writerows(all_records)

        if use_minio:
            out_bucket, out_prefix = _parse_minio_url(args.output)
            print(f"上传处理结果到 MinIO: {args.output}")
            upload_dir(mc, out_bucket, out_prefix, local_output)
            print("MinIO 上传完成")
        else:
            print(f"输出目录: {local_output}")

    from collections import Counter
    label_counts = Counter(r["label"] for r in all_records)
    print(f"\n处理完成，共 {len(all_records)} 张切片，标签分布: {dict(label_counts)}")


if __name__ == "__main__":
    main()
