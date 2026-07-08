"""CT image preprocessing: MinIO object/series -> HU volume -> model slices."""

import io
import os
import tempfile
from pathlib import Path

import numpy as np
import pydicom
import SimpleITK as sitk


def apply_windows(hu: np.ndarray) -> np.ndarray:
    """Convert one HU slice to 3 windowed uint8 channels."""

    def win(arr: np.ndarray, width: float, level: float) -> np.ndarray:
        lo, hi = level - width / 2, level + width / 2
        return np.clip((arr - lo) / (hi - lo) * 255, 0, 255).astype(np.uint8)

    return np.stack(
        [
            win(hu, width=80, level=40),
            win(hu, width=175, level=75),
            win(hu, width=2500, level=480),
        ],
        axis=-1,
    )


def download_and_load(object_key: str, minio_client) -> np.ndarray:
    """
    Download a CT attachment from MinIO and return a (Z, H, W) float32 HU volume.

    object_key can point to a single DICOM/NIfTI/NRRD object or to a DICOM series prefix.
    """
    bucket = os.getenv("MINIO_MEDICAL_BUCKET") or os.getenv("MINIO_BUCKET", "medical-imaging")

    try:
        data = minio_client.get_object(bucket, object_key)
        raw = data.read()
    except Exception:
        raw = None

    if raw:
        return _parse_raw(object_key, raw)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        objects = minio_client.list_objects(bucket, prefix=object_key, recursive=True)
        count = 0
        for obj in objects:
            obj_data = minio_client.get_object(bucket, obj.object_name)
            local = tmp_path / Path(obj.object_name).name
            local.write_bytes(obj_data.read())
            count += 1
        if count == 0:
            raise FileNotFoundError(f"CT attachment not found in MinIO: {object_key}")
        return _load_dicom_series(tmp_path)


def _parse_raw(object_key: str, raw: bytes) -> np.ndarray:
    """Load a single MinIO object as NIfTI, NRRD, or DICOM by extension and file content."""
    key_lower = object_key.lower()

    image_suffix = _image_suffix(key_lower, raw)
    if image_suffix:
        return _load_sitk_bytes(raw, image_suffix)

    if _looks_like_nifti(raw):
        suffix = ".nii.gz" if raw[:2] == b"\x1f\x8b" else ".nii"
        return _load_sitk_bytes(raw, suffix)

    if _looks_like_dicom(raw):
        return _dicom_to_hu(pydicom.dcmread(io.BytesIO(raw)))

    try:
        ds = pydicom.dcmread(io.BytesIO(raw), force=True)
    except Exception as exc:
        raise ValueError(
            "Uploaded CT attachment is not a supported DICOM, NIfTI, or NRRD file. "
            "Please upload original DICOM slices/series or a valid .nii/.nii.gz/.nrrd/.nhdr volume."
        ) from exc
    if not hasattr(ds, "PixelData"):
        raise ValueError(
            "Uploaded CT attachment does not contain DICOM pixel data. "
            "Please upload original DICOM slices/series or a valid .nii/.nii.gz/.nrrd/.nhdr volume."
        )
    return _dicom_to_hu(ds)


def _dicom_to_hu(ds) -> np.ndarray:
    arr = ds.pixel_array.astype(np.float32)
    slope = float(getattr(ds, "RescaleSlope", 1))
    intercept = float(getattr(ds, "RescaleIntercept", -1024))
    return (arr * slope + intercept)[np.newaxis]


def _looks_like_nifti(raw: bytes) -> bool:
    if raw[:2] == b"\x1f\x8b":
        return True
    return len(raw) >= 348 and raw[344:348] in (b"n+1\x00", b"ni1\x00", b"n+2\x00", b"ni2\x00")


def _looks_like_nrrd(raw: bytes) -> bool:
    return raw.startswith(b"NRRD")


def _looks_like_dicom(raw: bytes) -> bool:
    return len(raw) > 132 and raw[128:132] == b"DICM"


def _image_suffix(key_lower: str, raw: bytes) -> str:
    if key_lower.endswith(".nii.gz"):
        return ".nii.gz"
    for suffix in (".nii", ".nrrd", ".nhdr", ".mha", ".mhd"):
        if key_lower.endswith(suffix):
            return suffix
    if _looks_like_nrrd(raw):
        return ".nrrd"
    return ""


def _load_sitk_bytes(raw: bytes, suffix: str) -> np.ndarray:
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as f:
        f.write(raw)
        tmp_path = f.name
    try:
        img = sitk.ReadImage(tmp_path)
        return sitk.GetArrayFromImage(img).astype(np.float32)
    finally:
        os.unlink(tmp_path)


def _load_dicom_series(directory: Path) -> np.ndarray:
    """Load a local DICOM series directory."""
    reader = sitk.ImageSeriesReader()
    files = reader.GetGDCMSeriesFileNames(str(directory))
    if not files:
        raise ValueError(f"No DICOM series found in attachment prefix: {directory}")
    reader.SetFileNames(files)
    img = reader.Execute()
    arr = sitk.GetArrayFromImage(img).astype(np.float32)
    if img.HasMetaDataKey("0028|1053") and img.HasMetaDataKey("0028|1052"):
        slope = float(img.GetMetaData("0028|1053"))
        intercept = float(img.GetMetaData("0028|1052"))
        arr = arr * slope + intercept
    return arr


def volume_to_slices(hu_volume: np.ndarray, target_size: int = 512) -> tuple[np.ndarray, np.ndarray]:
    """
    Convert a (Z, H, W) HU volume to (N, 3, target_size, target_size) float32 slices.
    Invalid flat slices are skipped and output values are normalized to [0, 1].
    """
    import cv2

    valid_idx = np.where(hu_volume.std(axis=(1, 2)) > 100)[0]
    if len(valid_idx) == 0:
        valid_idx = np.arange(hu_volume.shape[0])

    slices = []
    for z in valid_idx:
        rgb = apply_windows(hu_volume[z])
        rgb = cv2.resize(rgb, (target_size, target_size))
        tensor = rgb.astype(np.float32) / 255.0
        slices.append(tensor.transpose(2, 0, 1))

    return np.stack(slices, axis=0), valid_idx
