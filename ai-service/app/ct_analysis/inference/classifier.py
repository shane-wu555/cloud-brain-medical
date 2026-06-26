"""EfficientNet-B0 分类推理：给定切片数组 → 体积级别分类结果"""

import os
from pathlib import Path

import numpy as np

CLASSES    = ["normal", "hemorrhage"]
CLASS_CN   = {
    "normal":     "未见明确异常",
    "hemorrhage": "颅内出血",
}

_session = None  # ONNX 推理 session（延迟加载）

# MinIO 默认路径（当本地文件不存在时自动下载）
# 优先查找项目内 models/ 目录（git 提交的模型），找不到再去 MinIO 下载
_REPO_MODEL           = str(Path(__file__).parent.parent.parent.parent / "models" / "classifier.onnx")
_DEFAULT_MINIO_OBJECT = "models/classifier.onnx"
_DEFAULT_LOCAL_CACHE  = "/tmp/ct_models/classifier.onnx"


def _get_session():
    global _session
    if _session is not None:
        return _session

    model_path = _resolve_model_path(
        env_key="CT_CLASSIFIER_MODEL",
        local_default=_DEFAULT_LOCAL_CACHE,
        minio_object=_DEFAULT_MINIO_OBJECT,
    )
    if model_path is None:
        return None

    import onnxruntime as ort
    opts = ort.SessionOptions()
    opts.inter_op_num_threads = 4
    opts.intra_op_num_threads = 4
    providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
    _session = ort.InferenceSession(model_path, sess_options=opts, providers=providers)
    return _session


def _resolve_model_path(env_key: str, local_default: str,
                        minio_object: str) -> str | None:
    """
    模型文件查找顺序：
      1. 环境变量指定的本地路径
      2. MinIO 下载到本地缓存
    """
    # 1. 环境变量指定路径
    local_path = os.getenv(env_key, local_default)
    if Path(local_path).exists():
        return local_path
    # 2. 项目 models/ 目录（git pull 后就有）
    if Path(_REPO_MODEL).exists():
        return _REPO_MODEL

    # 尝试从 MinIO 下载
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
        logging.getLogger(__name__).warning(f"模型文件不存在且 MinIO 下载失败: {e}")
        return None


def classify_volume(
    slices: np.ndarray,          # (N, 3, 512, 512) float32, values [0,1]
    batch_size: int = 16,
) -> dict:
    """
    对整个 CT 体积做分类推理。
    聚合策略：取所有切片概率的最大值（max pooling over slices）。

    Returns:
        {
          "label": "hemorrhage",
          "label_cn": "颅内出血",
          "confidence": 0.91,
          "slice_probs": [[0.1, 0.8, 0.1], ...],  # 每层概率
          "top_slice_idx": 23,                      # 最高概率切片
        }
    """
    session = _get_session()
    if session is None:
        return _mock_result()

    input_name  = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    all_probs = []
    for start in range(0, len(slices), batch_size):
        batch = slices[start:start + batch_size]
        logits = session.run([output_name], {input_name: batch})[0]
        probs  = _softmax(logits)
        all_probs.extend(probs.tolist())

    all_probs = np.array(all_probs)   # (N, 3)
    vol_probs  = all_probs.max(axis=0)  # (3,) - 体积级别：取最差情况
    label_id   = int(vol_probs.argmax())
    label      = CLASSES[label_id]
    confidence = float(vol_probs[label_id])

    top_slice_idx = int(all_probs[:, label_id].argmax()) if label != "normal" else -1

    return {
        "label":         label,
        "label_cn":      CLASS_CN[label],
        "confidence":    round(confidence, 4),
        "slice_probs":   all_probs.tolist(),
        "top_slice_idx": top_slice_idx,
    }


def _softmax(x: np.ndarray) -> np.ndarray:
    x = x - x.max(axis=1, keepdims=True)
    e = np.exp(x)
    return e / e.sum(axis=1, keepdims=True)


def _mock_result() -> dict:
    """模型文件不存在时的 fallback（开发阶段用）"""
    return {
        "label":         "normal",
        "label_cn":      "未见明确异常（mock）",
        "confidence":    0.86,
        "slice_probs":   [],
        "top_slice_idx": -1,
    }
