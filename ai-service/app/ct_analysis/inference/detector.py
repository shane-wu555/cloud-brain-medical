"""YOLOv8 病灶检测推理：仅在分类为异常时调用，返回各切片 bounding box"""

import os
from pathlib import Path

import cv2
import numpy as np

DETECT_CLASSES = ["hemorrhage", "ischemia"]

_yolo_model = None  # 延迟加载

_REPO_MODEL           = str(Path(__file__).parent.parent.parent.parent / "models" / "detector.onnx")
_DEFAULT_MINIO_OBJECT = "models/detector.onnx"
_DEFAULT_LOCAL_CACHE  = "/tmp/ct_models/detector.onnx"


def _get_model():
    global _yolo_model
    if _yolo_model is not None:
        return _yolo_model

    model_path = _resolve_model(
        env_key="CT_DETECTOR_MODEL",
        local_default=_DEFAULT_LOCAL_CACHE,
        minio_object=_DEFAULT_MINIO_OBJECT,
    )
    if model_path is None:
        return None

    from ultralytics import YOLO
    _yolo_model = YOLO(model_path)
    return _yolo_model


def _resolve_model(env_key: str, local_default: str,
                   minio_object: str) -> str | None:
    local_path = os.getenv(env_key, local_default)
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
        mc.fget_object(bucket, minio_object, local_path)
        return local_path
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning(f"检测器模型 MinIO 下载失败: {e}")
        return None


def detect_volume(
    slices: np.ndarray,          # (N, 3, 512, 512) float32 [0,1]
    valid_indices: np.ndarray,   # 原始体积中的切片索引（用于报告坐标）
    conf_threshold: float = 0.30,
    iou_threshold: float  = 0.50,
    top_k_slices: int     = 5,   # 只检测置信度最高的前 K 张切片（节省时间）
) -> list[dict]:
    """
    在切片上运行 YOLOv8 检测。
    只对 top_k_slices 张切片运行检测（由分类器的切片概率确定优先级）。

    Returns:
        [
          {
            "sliceIndex": 23,
            "bbox": [x1, y1, x2, y2],   # 像素坐标，图像尺寸 512x512
            "label": "hemorrhage",
            "confidence": 0.87,
          },
          ...
        ]
    """
    model = _get_model()
    if model is None:
        return []

    # 转为 uint8 BGR 图像（YOLO 期望的格式）
    results = []
    slice_indices = list(range(len(slices)))[:top_k_slices]  # 取前 K 张（调用方应已排好序）

    for local_idx in slice_indices:
        orig_z = int(valid_indices[local_idx])
        img_chw = slices[local_idx]                          # (3, H, W) [0,1]
        img_hwc = (img_chw.transpose(1, 2, 0) * 255).astype(np.uint8)
        img_bgr = cv2.cvtColor(img_hwc, cv2.COLOR_RGB2BGR)

        detections = model.predict(
            img_bgr,
            conf=conf_threshold,
            iou=iou_threshold,
            verbose=False,
        )

        for det in detections:
            if det.boxes is None or len(det.boxes) == 0:
                continue
            for box in det.boxes:
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().tolist()
                cls_id  = int(box.cls[0].item())
                conf    = float(box.conf[0].item())
                label   = DETECT_CLASSES[cls_id] if cls_id < len(DETECT_CLASSES) else "unknown"
                results.append({
                    "sliceIndex": orig_z,
                    "bbox":        [round(x1), round(y1), round(x2), round(y2)],
                    "label":       label,
                    "confidence":  round(conf, 4),
                })

    # 按置信度降序
    return sorted(results, key=lambda d: d["confidence"], reverse=True)


def top_abnormal_slices(
    slice_probs: list[list[float]],
    top_k: int = 5,
) -> list[int]:
    """
    从分类器的每层概率中，找出最可能存在病变的切片索引（local index）。
    slice_probs: shape (N, 3)，列0=normal, 1=hemorrhage, 2=ischemia
    """
    if not slice_probs:
        return []
    arr = np.array(slice_probs)
    abnormal_score = 1 - arr[:, 0]   # P(异常) = 1 - P(正常)
    return np.argsort(abnormal_score)[::-1][:top_k].tolist()
