"""CT DICOM 预处理：从 MinIO 下载 → HU 数组 → 3通道切片"""

import io
import os
import tempfile
from pathlib import Path

import numpy as np
import pydicom
import SimpleITK as sitk


def apply_windows(hu: np.ndarray) -> np.ndarray:
    """HU 数组 → 3通道图（脑窗/血窗/骨窗），输出 uint8 (H, W, 3)"""
    def win(arr, w, l):
        lo, hi = l - w / 2, l + w / 2
        return np.clip((arr - lo) / (hi - lo) * 255, 0, 255).astype(np.uint8)

    return np.stack([
        win(hu, w=80,   l=40),    # 脑组织
        win(hu, w=175,  l=75),    # 出血区域
        win(hu, w=2500, l=480),   # 颅骨
    ], axis=-1)


def download_and_load(object_key: str, minio_client) -> np.ndarray:
    """
    从 MinIO 下载 DICOM/NIfTI，返回 (N, H, W) float32 HU 数组。
    object_key 可以是单 DICOM 文件，也可以是文件夹前缀（多文件序列）。
    """
    bucket = os.getenv("MINIO_BUCKET", "medical-files")

    # 尝试单文件下载
    try:
        data = minio_client.get_object(bucket, object_key)
        raw  = data.read()
    except Exception:
        raw = None

    if raw:
        return _parse_raw(object_key, raw)

    # 尝试目录前缀（多切片 DICOM 序列）
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        objects = minio_client.list_objects(bucket, prefix=object_key, recursive=True)
        for obj in objects:
            obj_data = minio_client.get_object(bucket, obj.object_name)
            local = tmp_path / Path(obj.object_name).name
            local.write_bytes(obj_data.read())

        return _load_dicom_series(tmp_path)


def _parse_raw(object_key: str, raw: bytes) -> np.ndarray:
    """根据扩展名选择解析方式"""
    key_lower = object_key.lower()

    if key_lower.endswith(".nii.gz") or key_lower.endswith(".nii"):
        with tempfile.NamedTemporaryFile(suffix=".nii.gz", delete=False) as f:
            f.write(raw)
            tmp_path = f.name
        try:
            img = sitk.ReadImage(tmp_path)
            arr = sitk.GetArrayFromImage(img).astype(np.float32)
            return arr
        finally:
            os.unlink(tmp_path)

    # DICOM 单文件
    ds = pydicom.dcmread(io.BytesIO(raw))
    arr = ds.pixel_array.astype(np.float32)
    slope = float(getattr(ds, "RescaleSlope", 1))
    intercept = float(getattr(ds, "RescaleIntercept", -1024))
    arr = arr * slope + intercept
    return arr[np.newaxis]  # (1, H, W)


def _load_dicom_series(directory: Path) -> np.ndarray:
    """从本地目录读取 DICOM 序列"""
    reader = sitk.ImageSeriesReader()
    files  = reader.GetGDCMSeriesFileNames(str(directory))
    if not files:
        raise ValueError(f"目录中没有 DICOM 文件: {directory}")
    reader.SetFileNames(files)
    img = reader.Execute()
    arr = sitk.GetArrayFromImage(img).astype(np.float32)
    if img.HasMetaDataKey("0028|1053") and img.HasMetaDataKey("0028|1052"):
        slope     = float(img.GetMetaData("0028|1053"))
        intercept = float(img.GetMetaData("0028|1052"))
        arr = arr * slope + intercept
    return arr  # (Z, Y, X)


def volume_to_slices(hu_volume: np.ndarray, target_size: int = 512) -> np.ndarray:
    """
    (Z, H, W) float32 HU → (Z, 3, target_size, target_size) float32
    过滤无效切片（全黑/床板），归一化到 [0, 1]
    """
    import cv2

    valid_idx = np.where(hu_volume.std(axis=(1, 2)) > 100)[0]
    slices = []

    for z in valid_idx:
        rgb = apply_windows(hu_volume[z])                           # (H, W, 3) uint8
        rgb = cv2.resize(rgb, (target_size, target_size))           # resize
        tensor = rgb.astype(np.float32) / 255.0                     # [0,1]
        tensor = tensor.transpose(2, 0, 1)                          # (3, H, W)
        slices.append(tensor)

    return np.stack(slices, axis=0), valid_idx  # (N_valid, 3, H, W), valid slice indices
