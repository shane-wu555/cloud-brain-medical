"""Metal artifact severity classifier inference."""

from __future__ import annotations

import os
from pathlib import Path

import numpy as np

from .ort_runtime import preload_cuda_dlls

CLASSES = ["normal", "small_metal", "moderate_metal", "severe_metal"]
CLASS_CN = {
    "normal": "未见明显金属伪影",
    "small_metal": "轻度金属伪影",
    "moderate_metal": "中度金属伪影",
    "severe_metal": "重度金属伪影",
}

_session = None

_REPO_MODEL = str(
    Path(__file__).parent.parent.parent.parent
    / "models"
    / "metal_severity"
    / "metal_classifier_severity.onnx"
)
_DEFAULT_MINIO_OBJECT = "models/metal_severity/metal_classifier_severity.onnx"
_DEFAULT_LOCAL_CACHE = "/tmp/ct_models/metal_classifier_severity.onnx"


def classify_metal_artifact(slices: np.ndarray, batch_size: int = 16) -> dict:
    """Classify metal artifact severity for a CT volume."""
    session = _get_session()
    if session is None or slices.size == 0:
        return _fallback_result(enabled=session is not None)

    model_input = _normalize_channelwise(slices.astype(np.float32, copy=False))
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    all_probs = []
    for start in range(0, len(model_input), batch_size):
        batch = model_input[start:start + batch_size]
        logits = session.run([output_name], {input_name: batch})[0]
        all_probs.extend(_softmax(logits).tolist())

    all_probs = np.asarray(all_probs, dtype=np.float32)
    volume_probs = all_probs.max(axis=0)
    label_id = int(volume_probs.argmax())
    label = CLASSES[label_id]
    confidence = float(volume_probs[label_id])
    top_slice_idx = int(all_probs[:, label_id].argmax()) if len(all_probs) else -1

    return {
        "enabled": True,
        "label": label,
        "labelCn": CLASS_CN[label],
        "confidence": round(confidence, 4),
        "classProbs": {
            cls: round(float(prob), 4)
            for cls, prob in zip(CLASSES, volume_probs)
        },
        "topSliceIdx": top_slice_idx,
    }


def _get_session():
    global _session
    if _session is not None:
        return _session

    model_path = _resolve_model_path()
    if model_path is None:
        return None

    import onnxruntime as ort
    preload_cuda_dlls(ort)

    opts = ort.SessionOptions()
    opts.inter_op_num_threads = int(os.getenv("CT_ONNX_INTER_OP_THREADS", "1"))
    opts.intra_op_num_threads = int(os.getenv("CT_ONNX_INTRA_OP_THREADS", "1"))
    providers = [
        provider
        for provider in ["CUDAExecutionProvider", "CPUExecutionProvider"]
        if provider in ort.get_available_providers()
    ]
    _session = ort.InferenceSession(model_path, sess_options=opts, providers=providers)
    return _session


def _resolve_model_path() -> str | None:
    local_path = os.getenv("CT_METAL_CLASSIFIER_MODEL", _DEFAULT_LOCAL_CACHE)
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
        import logging

        logging.getLogger(__name__).warning(f"Metal artifact model unavailable: {exc}")
        return None


def _normalize_channelwise(slices: np.ndarray) -> np.ndarray:
    normalized = slices.copy()
    for i in range(normalized.shape[0]):
        for c in range(normalized.shape[1]):
            channel = normalized[i, c]
            valid = channel != 0
            values = channel[valid] if np.any(valid) else channel.reshape(-1)
            mean = float(values.mean())
            std = float(values.std())
            normalized[i, c] = (channel - mean) / (std + 1e-8)
    return normalized.astype(np.float32, copy=False)


def _softmax(x: np.ndarray) -> np.ndarray:
    x = x - x.max(axis=1, keepdims=True)
    exp = np.exp(x)
    return exp / exp.sum(axis=1, keepdims=True)


def _fallback_result(enabled: bool = False) -> dict:
    return {
        "enabled": enabled,
        "label": "unknown",
        "labelCn": "金属伪影模型未启用",
        "confidence": 0.0,
        "classProbs": {},
        "topSliceIdx": -1,
    }
