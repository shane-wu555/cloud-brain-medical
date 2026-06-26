"""
阶段①（CQ500 专用）：预处理脚本
CQ500 zip 结构:
    CQ500-CT-X.zip
    └── CQ500CTX CQ500CTX/
        └── Unknown Study/
            └── CT 2.55mm/          ← 序列名称可能不同
                ├── CT000000.dcm
                ├── CT000001.dcm
                ...

从 MinIO 下载 zip → 解压 → 读 DICOM → 3通道PNG切片 → 上传回 MinIO

用法:
    python training/preprocess_cq500.py \
        --input  minio://training-data/CQ500 \
        --output minio://training-output/cq500_processed

    # 或本地测试（跳过 MinIO）:
    python training/preprocess_cq500.py \
        --input  F:/ShiXunClass/data/CQ500/CQ500_orig \
        --output F:/tmp/cq500_processed \
        --local
"""

import argparse
import csv
import io
import json
import os
import platform
import shutil
import subprocess
import tempfile
from collections import Counter
from pathlib import Path

import cv2
import numpy as np
import SimpleITK as sitk
from sklearn.model_selection import StratifiedKFold
from tqdm import tqdm

LABEL_MAP = {"normal": 0, "hemorrhage": 1}


# ── 窗宽/窗位 ─────────────────────────────────────────────────────────────────

def apply_windows(hu: np.ndarray) -> np.ndarray:
    """HU 数组 → 3通道 uint8（脑窗/血窗/骨窗）"""
    def win(arr, w, l):
        lo, hi = l - w / 2, l + w / 2
        return np.clip((arr - lo) / (hi - lo) * 255, 0, 255).astype(np.uint8)

    return np.stack([
        win(hu, w=80,   l=40),    # 脑组织
        win(hu, w=175,  l=75),    # 出血区域
        win(hu, w=2500, l=480),   # 颅骨
    ], axis=-1)


# ── zip 解压 ──────────────────────────────────────────────────────────────────

def extract_zip(zip_path: Path, dest: Path) -> None:
    """解压 zip，优先用 Python zipfile，失败则调用系统工具"""
    import zipfile
    try:
        with zipfile.ZipFile(zip_path, "r") as z:
            z.extractall(dest)
        return
    except zipfile.BadZipFile:
        pass

    # fallback：系统工具
    if platform.system() == "Windows":
        subprocess.run(
            ["powershell", "-Command",
             f'Expand-Archive -Path "{zip_path}" -DestinationPath "{dest}" -Force'],
            check=True, capture_output=True,
        )
    else:
        subprocess.run(["unzip", "-q", str(zip_path), "-d", str(dest)], check=True)


# ── DICOM 加载 ────────────────────────────────────────────────────────────────

def load_best_series(root: Path) -> np.ndarray:
    """
    用 SimpleITK GDCM Series 检测找到切片最多的序列并加载。
    避免把多个 Series 的 dcm 混在一起读（会产生 Non-uniform sampling 警告）。
    返回 (N, H, W) float32 HU 数组。
    """
    # 找所有含 dcm 的子目录
    dcm_dirs = sorted({dcm.parent for dcm in root.rglob("*.dcm")})
    if not dcm_dirs:
        raise FileNotFoundError(f"解压目录中没有 .dcm 文件: {root}")

    best_files: list[str] = []
    for d in dcm_dirs:
        series_ids = sitk.ImageSeriesReader.GetGDCMSeriesIDs(str(d))
        for sid in series_ids:
            files = sitk.ImageSeriesReader.GetGDCMSeriesFileNames(str(d), sid)
            if len(files) > len(best_files):
                best_files = list(files)

    if not best_files:
        raise ValueError(f"无法识别有效 DICOM 序列: {root}")

    reader = sitk.ImageSeriesReader()
    reader.SetFileNames(best_files)
    reader.MetaDataDictionaryArrayUpdateOn()
    img = reader.Execute()
    arr = sitk.GetArrayFromImage(img).astype(np.float32)
    return arr  # (Z, H, W)


# ── 切片处理 ──────────────────────────────────────────────────────────────────

def process_patient(patient_id: str, hu_volume: np.ndarray,
                    label: str, out_images: Path) -> list[dict]:
    """
    将一个病人的 CT 体积拆成 PNG 切片并保存，返回该病人的标注记录列表。
    过滤无效切片（平均 HU < -900，通常是空气/床板）。
    """
    records = []
    n = hu_volume.shape[0]
    # 有效切片判断：标准差 > 100 HU（有组织信息），排除纯空气/床板层
    valid_mask = hu_volume.std(axis=(1, 2)) > 100

    for z in range(n):
        if not valid_mask[z]:
            continue

        rgb = apply_windows(hu_volume[z])
        rgb = cv2.resize(rgb, (512, 512))

        fname = f"{patient_id}_z{z:03d}.png"
        cv2.imwrite(str(out_images / fname),
                    cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR))

        records.append({
            "patient_id":  patient_id,
            "slice_name":  fname,
            "slice_index": z,
            "label":       label,
            "label_id":    LABEL_MAP.get(label, 0),
            "fold":        -1,   # 后续填充
        })

    return records


# ── 5-fold 划分 ───────────────────────────────────────────────────────────────

def assign_folds(records: list[dict], n_folds: int = 5) -> list[dict]:
    """按病人 ID 做 StratifiedKFold，避免同一病人跨 fold"""
    patients = sorted({r["patient_id"] for r in records})
    first_label = {
        p: next(r["label_id"] for r in records if r["patient_id"] == p)
        for p in patients
    }
    p_arr = np.array(patients)
    l_arr = np.array([first_label[p] for p in patients])

    kf = StratifiedKFold(n_splits=n_folds, shuffle=True, random_state=42)
    fold_map: dict[str, int] = {}
    for fold_idx, (_, val_idx) in enumerate(kf.split(p_arr, l_arr)):
        for p in p_arr[val_idx]:
            fold_map[p] = fold_idx

    for r in records:
        r["fold"] = fold_map[r["patient_id"]]
    return records


# ── 主流程 ────────────────────────────────────────────────────────────────────

def run_local(input_dir: Path, output_dir: Path) -> None:
    """本地模式：从本地 zip 目录处理，输出到本地"""
    labels_path = input_dir / "labels.json"
    if not labels_path.exists():
        # 尝试 reads.csv → 生成 labels.json
        csv_path = input_dir / "reads.csv"
        if csv_path.exists():
            labels = _convert_reads_csv(csv_path)
            labels_path.write_text(json.dumps(labels, ensure_ascii=False, indent=2))
        else:
            raise FileNotFoundError(f"找不到 labels.json 或 reads.csv: {input_dir}")

    with open(labels_path, encoding="utf-8") as f:
        annotations: list[dict] = json.load(f)
    ann_map = {a["patientId"]: a["label"] for a in annotations}

    out_images = output_dir / "images"
    out_images.mkdir(parents=True, exist_ok=True)

    zip_files = sorted(input_dir.glob("CQ500-CT-*.zip"))
    all_records: list[dict] = []

    for zip_path in tqdm(zip_files, desc="处理患者"):
        patient_id = zip_path.stem          # e.g. "CQ500-CT-1"
        label = ann_map.get(patient_id, "normal")

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            try:
                extract_zip(zip_path, tmp_path)
                hu_volume = load_best_series(tmp_path)
            except Exception as e:
                tqdm.write(f"[跳过] {patient_id}: {e}")
                continue

            records = process_patient(patient_id, hu_volume, label, out_images)
            all_records.extend(records)

    _save_manifest(all_records, output_dir)


def run_minio(input_prefix: str, output_prefix: str) -> None:
    """MinIO 模式：从 MinIO 下载 zip，处理后上传结果"""
    from minio_io import client, download_dir, upload_dir, upload_file

    mc = client()
    in_bucket,  in_pfx  = _parse_minio(input_prefix)
    out_bucket, out_pfx = _parse_minio(output_prefix)

    # 下载 labels.json
    with tempfile.TemporaryDirectory() as workspace:
        ws = Path(workspace)
        lbl_local = ws / "labels.json"
        mc.fget_object(in_bucket, f"{in_pfx}/labels.json", str(lbl_local))
        with open(lbl_local, encoding="utf-8") as f:
            annotations: list[dict] = json.load(f)
        ann_map = {a["patientId"]: a["label"] for a in annotations}

        out_images = ws / "output" / "images"
        out_images.mkdir(parents=True, exist_ok=True)

        # 列出 zip 对象
        zip_objects = [
            obj.object_name
            for obj in mc.list_objects(in_bucket, prefix=in_pfx + "/", recursive=False)
            if obj.object_name.endswith(".zip")
        ]
        all_records: list[dict] = []

        for obj_name in tqdm(zip_objects, desc="处理患者（MinIO）"):
            patient_id = Path(obj_name).stem
            label = ann_map.get(patient_id, "normal")

            with tempfile.TemporaryDirectory() as tmp:
                tmp_path = Path(tmp)
                zip_local = tmp_path / Path(obj_name).name
                mc.fget_object(in_bucket, obj_name, str(zip_local))

                try:
                    extract_zip(zip_local, tmp_path / "extracted")
                    hu_volume = load_best_series(tmp_path / "extracted")
                except Exception as e:
                    tqdm.write(f"[跳过] {patient_id}: {e}")
                    continue

                records = process_patient(patient_id, hu_volume, label, out_images)
                all_records.extend(records)

        _save_manifest(all_records, ws / "output")

        # 上传到 MinIO
        from minio_io import ensure_bucket
        ensure_bucket(mc, out_bucket)
        print(f"上传处理结果到 MinIO: {output_prefix}")
        upload_dir(mc, out_bucket, out_pfx, ws / "output")
        print("MinIO 上传完成")

    counts = Counter(r["label"] for r in all_records)
    print(f"\n预处理完成，共 {len(all_records)} 张切片，分布: {dict(counts)}")


def _save_manifest(records: list[dict], output_dir: Path) -> None:
    if not records:
        print("[错误] 没有成功处理任何病人，请检查日志中的 [跳过] 信息")
        return
    records = assign_folds(records)
    manifest = output_dir / "manifest.csv"
    with open(manifest, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=records[0].keys())
        writer.writeheader()
        writer.writerows(records)
    counts = Counter(r["label"] for r in records)
    print(f"\n预处理完成: {len(records)} 张切片，分布: {dict(counts)}")
    print(f"标注清单: {manifest}")


def _parse_minio(url: str) -> tuple[str, str]:
    path = url[len("minio://"):]
    bucket, _, prefix = path.partition("/")
    return bucket, prefix.rstrip("/")


def _convert_reads_csv(csv_path: Path) -> list[dict]:
    """本地 reads.csv → labels.json（多数投票）"""
    import csv as csv_mod
    with open(csv_path, encoding="utf-8-sig") as f:
        rows = list(csv_mod.DictReader(f))
    result = []
    for row in rows:
        votes = sum(int(row.get(f"R{r}:ICH", 0)) for r in [1, 2, 3])
        result.append({"patientId": row["name"],
                       "label": "hemorrhage" if votes >= 2 else "normal",
                       "bboxes": []})
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="CQ500 预处理")
    parser.add_argument("--input",  required=True,
                        help="MinIO 路径 minio://bucket/prefix 或本地 zip 目录")
    parser.add_argument("--output", required=True,
                        help="MinIO 路径 minio://bucket/prefix 或本地输出目录")
    parser.add_argument("--local",  action="store_true",
                        help="强制本地模式（不使用 MinIO）")
    args = parser.parse_args()

    if args.local or not args.input.startswith("minio://"):
        run_local(Path(args.input), Path(args.output))
    else:
        run_minio(args.input, args.output)


if __name__ == "__main__":
    main()
