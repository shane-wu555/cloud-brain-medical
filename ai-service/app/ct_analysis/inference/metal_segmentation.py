"""Metal artifact segmentation inference."""

from __future__ import annotations

import logging
import os
from pathlib import Path

import numpy as np

HU_MIN = -1000.0
HU_MAX = 3000.0

_session = None
log = logging.getLogger(__name__)

_REPO_MODEL = str(
    Path(__file__).parent.parent.parent.parent
    / "models"
    / "metal_segmentation"
    / "metal_segmentation.onnx"
)
_DEFAULT_MINIO_OBJECT = "models/metal_segmentation/metal_segmentation.onnx"
_DEFAULT_LOCAL_CACHE = "/tmp/ct_models/metal_segmentation.onnx"


def segment_metal_artifact(
    hu_volume: np.ndarray,
    valid_indices: np.ndarray | list[int] | None = None,
    batch_size: int = 8,
    threshold: float | None = None,
    top_k: int = 8,
) -> dict:
    """Run metal/artifact segmentation and return a compact summary."""
    session = _get_session()
    if session is None or hu_volume.size == 0:
        return _fallback_result(enabled=session is not None)

    if hu_volume.ndim == 2:
        hu_volume = hu_volume[np.newaxis]

    valid = np.asarray(valid_indices if valid_indices is not None else np.arange(len(hu_volume)), dtype=np.int32)
    if len(valid) == 0:
        return _fallback_result(enabled=True)

    threshold = float(os.getenv("CT_METAL_SEG_THRESHOLD", threshold or 0.5))
    image_size = _input_size(session)
    model_input = _prepare_slices(hu_volume[valid], image_size)

    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    slice_summaries: list[dict] = []
    total_fg = 0
    total_pixels = 0

    for start in range(0, len(model_input), batch_size):
        batch = model_input[start:start + batch_size]
        logits = session.run([output_name], {input_name: batch})[0]
        probs = _sigmoid(logits[:, 0])
        for offset, prob in enumerate(probs):
            volume_idx = int(valid[start + offset])
            mask = prob >= threshold
            fg = int(mask.sum())
            total_fg += fg
            total_pixels += int(mask.size)
            if fg == 0:
                continue
            slice_summaries.append(
                {
                    "sliceIndex": volume_idx,
                    "areaRatio": round(float(fg / mask.size), 6),
                    "maxProb": round(float(prob.max()), 4),
                    "meanProb": round(float(prob[mask].mean()), 4),
                    "bbox": _mask_bbox(mask),
                }
            )

    slice_summaries.sort(key=lambda item: (item["areaRatio"], item["maxProb"]), reverse=True)
    top_slices = slice_summaries[:top_k]
    fg_ratio = float(total_fg / total_pixels) if total_pixels else 0.0
    confidence = max((item["maxProb"] for item in top_slices), default=0.0)

    return {
        "enabled": True,
        "threshold": threshold,
        "hasArtifactRegion": bool(top_slices),
        "affectedSlices": len(slice_summaries),
        "totalSlices": int(len(valid)),
        "foregroundRatio": round(fg_ratio, 6),
        "confidence": round(float(confidence), 4),
        "topSlices": top_slices,
    }


def _get_session():
    global _session
    if _session is not None:
        return _session

    model_path = _resolve_model_path()
    if model_path is None:
        return None

    import onnxruntime as ort

    opts = ort.SessionOptions()
    opts.inter_op_num_threads = 4
    opts.intra_op_num_threads = 4
    _session = ort.InferenceSession(
        model_path,
        sess_options=opts,
        providers=["CUDAExecutionProvider", "CPUExecutionProvider"],
    )
    return _session


def _resolve_model_path() -> str | None:
    local_path = os.getenv("CT_METAL_SEGMENTATION_MODEL", _DEFAULT_LOCAL_CACHE)
    if Path(local_path).exists():
        return local_path
    if Path(_REPO_MODEL).exists():
        return _REPO_MODEL

    try:
        from minio import Minio

        bucket = os.getenv("MINIO_MODEL_BUCKET", "models")
        mc = Minio(
            os.getenv("MINIO_ENDPOINT", "localhost:9000"),
            access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
            secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
            secure=os.getenv("MINIO_SECURE", "false").lower() == "true",
        )
        Path(local_path).parent.mkdir(parents=True, exist_ok=True)
        mc.fget_object(bucket, _DEFAULT_MINIO_OBJECT, local_path)
        return local_path
    except Exception as exc:
        log.warning(f"Metal segmentation model unavailable: {exc}")
        return None


def _input_size(session) -> int:
    try:
        size = int(session.get_inputs()[0].shape[-1])
        return size if size > 0 else 512
    except Exception:
        return 512


def _prepare_slices(hu_slices: np.ndarray, image_size: int) -> np.ndarray:
    import cv2

    prepared = []
    for image in hu_slices.astype(np.float32, copy=False):
        image = np.clip(image, HU_MIN, HU_MAX)
        image = (image - HU_MIN) / (HU_MAX - HU_MIN)
        if image.shape != (image_size, image_size):
            image = cv2.resize(image, (image_size, image_size), interpolation=cv2.INTER_LINEAR)
        prepared.append(image[None])
    return np.stack(prepared, axis=0).astype(np.float32, copy=False)


def _mask_bbox(mask: np.ndarray) -> list[int]:
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return [0, 0, 0, 0]
    return [int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)]


def _sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


def _fallback_result(enabled: bool = False) -> dict:
    return {
        "enabled": enabled,
        "threshold": float(os.getenv("CT_METAL_SEG_THRESHOLD", "0.5")),
        "hasArtifactRegion": False,
        "affectedSlices": 0,
        "totalSlices": 0,
        "foregroundRatio": 0.0,
        "confidence": 0.0,
        "topSlices": [],
    }
