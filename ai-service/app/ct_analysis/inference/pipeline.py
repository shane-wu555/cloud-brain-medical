"""
CT 推理主流程（三阶段）：
  1. 下载 + 预处理 DICOM → 切片数组
  2. EfficientNet-B0 分类
  3. 若异常 → YOLOv8 检测框
返回结构化报告字段。
"""

import os
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Any

import numpy as np

from .preprocessing import download_and_load, volume_to_slices
from .classifier    import classify_volume
from .detector      import detect_volume, top_abnormal_slices
from .metal_classifier import classify_metal_artifact
from .metal_segmentation import segment_metal_artifact
from .lesion_segmentation import lesion_regions, segment_lesion

log = logging.getLogger(__name__)


FINDINGS_TEMPLATE = {
    "normal": (
        "头颅CT平扫示脑实质密度均匀，灰白质分界清晰，"
        "脑室系统大小形态正常，中线结构居中，"
        "颅骨内板下未见明显异常密度影。"
    ),
    "hemorrhage": (
        "头颅CT平扫示颅内见高密度影，"
        "病灶位于 {location}，大小约 {size}，"
        "周围可见低密度水肿带，中线结构{midline}。"
    ),
    "ischemia": (
        "头颅CT平扫示 {location} 见低密度影，"
        "考虑脑梗死可能，累及范围约 {size}，"
        "中线结构{midline}，脑沟脑裂{sulci}。"
    ),
}

CONCLUSION_TEMPLATE = {
    "normal":     "未见明确急性颅内出血或梗死征象。",
    "hemorrhage": "颅内出血可能性大。",
    "ischemia":   "脑缺血性改变可能。",
}

RISK_ADVICE = {
    "normal":     "建议结合临床表现及既往资料综合评估，必要时随访观察。",
    "hemorrhage": "高风险：颅内出血征象明确，建议立即按急诊流程处理。",
    "ischemia":   "存在脑缺血性改变征象，建议完善 DWI/MRI 检查并联系神经内科评估。",
}


def _bbox_to_location(detections: list[dict]) -> tuple[str, str]:
    """从检测框粗估病灶位置描述"""
    if not detections:
        return "待定区域", "不详"

    top = detections[0]
    x1, y1, x2, y2 = top["bbox"]
    cx = (x1 + x2) / 2
    cy = (y1 + y2) / 2

    h_pos = "左侧" if cx < 256 else "右侧"
    v_pos = "额叶" if cy < 170 else ("颞顶叶" if cy < 340 else "枕叶")
    w = abs(x2 - x1) / 512 * 10  # 粗略估算 cm（假设 FOV=25cm）
    h = abs(y2 - y1) / 512 * 10
    size = f"{w:.1f}×{h:.1f}cm"

    return f"{h_pos}{v_pos}", size


def _metal_report_text(metal_result: dict) -> tuple[str, str]:
    if not metal_result.get("enabled"):
        return "", ""

    label = metal_result.get("label", "unknown")
    label_cn = metal_result.get("labelCn", "金属伪影评估未知")
    finding = f"金属伪影情况：{label_cn}。"

    if label in {"moderate_metal", "severe_metal"}:
        advice = "图像存在较明显金属伪影，相关区域判断受限，必要时可复查或补充其他检查。"
    elif label == "small_metal":
        advice = "图像存在轻度金属伪影，邻近高密度区域需纳入综合评估。"
    else:
        advice = "未见明显影响诊断的金属伪影。"
    return finding, advice


def _metal_seg_report_text(seg_result: dict) -> tuple[str, str]:
    if not seg_result.get("enabled"):
        return "", ""
    if not seg_result.get("hasArtifactRegion"):
        return "未见明确金属伪影相关异常区域。", ""

    affected = int(seg_result.get("affectedSlices", 0))
    total = int(seg_result.get("totalSlices", 0))
    fg_ratio = float(seg_result.get("foregroundRatio", 0.0))
    finding = (
        "可见金属伪影相关区域，"
        f"累及 {affected}/{total} 层，前景占比 {fg_ratio:.2%}，"
        "相关层面图像质量受影响。"
    )
    advice = "金属伪影相关层面的病灶判断需谨慎，必要时补充其他检查。"
    return finding, advice


def _lesion_seg_report_text(seg_result: dict) -> tuple[str, str]:
    if not seg_result.get("enabled"):
        return "", ""
    if not seg_result.get("hasLesionRegion"):
        return "未见明确局灶性异常密度区域。", ""

    affected = int(seg_result.get("affectedSlices", 0))
    total = int(seg_result.get("totalSlices", 0))
    fg_ratio = float(seg_result.get("foregroundRatio", 0.0))
    finding = (
        "可见疑似病灶区域，"
        f"累及 {affected}/{total} 层，前景占比 {fg_ratio:.2%}，"
        "请结合病灶部位及范围综合判断。"
    )
    advice = "建议结合病灶部位、范围及临床表现制定后续处理方案。"
    return finding, advice


def _run_small_models_parallel(
    hu_volume,
    slices,
    valid_indices,
) -> tuple[dict, dict, dict, dict, list[dict], dict[str, int]]:
    workers = max(1, int(os.getenv("CT_INFERENCE_PARALLEL_WORKERS", "4")))
    max_workers = max(1, int(os.getenv("CT_INFERENCE_MAX_PARALLEL_WORKERS", "8")))
    workers = min(workers, max_workers)
    timings: dict[str, int] = {}
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="ct-ai") as executor:
        classifier_future = executor.submit(_timed_call, "classifier", classify_volume, slices)
        metal_future = executor.submit(_timed_call, "metal_classifier", classify_metal_artifact, slices)
        metal_seg_future = executor.submit(_timed_call, "metal_segmentation", segment_metal_artifact, hu_volume, valid_indices)
        lesion_seg_future = executor.submit(_timed_call, "lesion_segmentation", segment_lesion, hu_volume, valid_indices)

        clf_result, timings["classifier"] = classifier_future.result()
        detections, timings["detector"] = _timed_call("detector", _detect_if_needed, clf_result, slices, valid_indices)
        metal_result, timings["metal_classifier"] = metal_future.result()
        metal_seg_result, timings["metal_segmentation"] = metal_seg_future.result()
        lesion_seg_result, timings["lesion_segmentation"] = lesion_seg_future.result()

    timings["parallel_total"] = max(
        timings.get("classifier", 0) + timings.get("detector", 0),
        timings.get("metal_classifier", 0),
        timings.get("metal_segmentation", 0),
        timings.get("lesion_segmentation", 0),
    )
    return clf_result, metal_result, metal_seg_result, lesion_seg_result, detections, timings


def _limit_model_slices(
    hu_volume,
    slices,
    valid_indices,
) -> tuple[Any, Any, Any, dict[str, int]]:
    max_slices = int(os.getenv("CT_MAX_MODEL_SLICES", "0"))
    total = int(len(slices))
    if max_slices <= 0 or total <= max_slices:
        return hu_volume, slices, valid_indices, {
            "originalSlices": total,
            "modelSlices": total,
        }

    selected = np.unique(np.linspace(0, total - 1, max_slices, dtype=np.int32))
    selected_valid_indices = valid_indices[selected]
    return (
        hu_volume,
        slices[selected],
        selected_valid_indices,
        {
            "originalSlices": total,
            "modelSlices": int(len(selected)),
        },
    )


def _timed_call(name: str, fn, *args):
    started = time.perf_counter()
    result = fn(*args)
    elapsed_ms = int((time.perf_counter() - started) * 1000)
    log.info("[CT推理] %s 耗时 %sms", name, elapsed_ms)
    return result, elapsed_ms


def _detect_if_needed(clf_result: dict, slices, valid_indices) -> list[dict]:
    label = clf_result["label"]
    slice_probs = clf_result.get("slice_probs")
    detections: list[dict] = []
    if label != "normal" and slice_probs:
        top_k = max(1, int(os.getenv("CT_DETECTOR_TOP_K_SLICES", "10")))
        top_slices = top_abnormal_slices(slice_probs, top_k=top_k)
        sorted_slices = slices[top_slices] if top_slices else slices[:top_k]
        sorted_indices = valid_indices[top_slices] if top_slices else valid_indices[:top_k]
        detections = detect_volume(sorted_slices, sorted_indices)
        log.info(f"[CT推理] 检测框数量: {len(detections)}")
    return detections


def run(object_key: str, order_id: str, clinical_context: str = "") -> dict[str, Any]:
    """
    完整推理流程入口。
    返回:
        findings, conclusion, riskAdvice, confidence,
        abnormalRegions (bboxes), modelVersion
    """
    model_version = os.getenv("CT_MODEL_VERSION", "ct-head-v1.0")
    total_started = time.perf_counter()
    timings: dict[str, int] = {}

    # ── 1. 下载 + 预处理 ──────────────────────────────────
    minio_client = _get_minio_client()
    log.info(f"[CT推理] 开始下载 objectKey={object_key}")
    started = time.perf_counter()
    hu_volume = download_and_load(object_key, minio_client)
    timings["download_load"] = int((time.perf_counter() - started) * 1000)
    started = time.perf_counter()
    slices, valid_indices = volume_to_slices(hu_volume)
    timings["preprocess_slices"] = int((time.perf_counter() - started) * 1000)
    log.info(f"[CT推理] 体积 shape={hu_volume.shape}, 有效切片={len(slices)}")
    model_hu_volume, model_slices, model_valid_indices, slice_timings = _limit_model_slices(
        hu_volume,
        slices,
        valid_indices,
    )
    timings.update(slice_timings)
    if slice_timings["modelSlices"] != slice_timings["originalSlices"]:
        log.info(
            "[CT推理] 模型切片采样 %s -> %s",
            slice_timings["originalSlices"],
            slice_timings["modelSlices"],
        )

    # ── 2. 并行运行互不依赖的小模型 ─────────────────────────
    clf_result, metal_result, metal_seg_result, lesion_seg_result, detections, model_timings = _run_small_models_parallel(
        model_hu_volume,
        model_slices,
        model_valid_indices,
    )
    timings.update(model_timings)
    label       = clf_result["label"]
    confidence  = clf_result["confidence"]
    _require_model_outputs(metal_result, metal_seg_result, lesion_seg_result)
    log.info(f"[CT推理] 分类结果: {label} ({confidence:.2%})")

    seg_regions = lesion_regions(lesion_seg_result, label=label if label != "normal" else "lesion")
    if seg_regions:
        existing_keys = {(item.get("sliceIndex"), tuple(item.get("bbox", []))) for item in detections}
        for region in seg_regions:
            key = (region.get("sliceIndex"), tuple(region.get("bbox", [])))
            if key not in existing_keys:
                detections.append(region)
        detections = sorted(detections, key=lambda item: float(item.get("confidence", 0.0)), reverse=True)

    # ── 4. 生成报告字段 ───────────────────────────────────
    location, size = _bbox_to_location(detections)
    midline = "居中" if not detections else "轻度偏移" if len(detections) == 1 else "明显偏移"

    findings = FINDINGS_TEMPLATE[label].format(
        location=location, size=size, midline=midline, sulci="无明显增宽"
    )
    conclusion = CONCLUSION_TEMPLATE[label].format(confidence=confidence)
    risk_advice = RISK_ADVICE[label]
    metal_finding, metal_advice = _metal_report_text(metal_result)
    metal_seg_finding, metal_seg_advice = _metal_seg_report_text(metal_seg_result)
    lesion_seg_finding, lesion_seg_advice = _lesion_seg_report_text(lesion_seg_result)
    if metal_finding:
        findings = f"{findings} {metal_finding}"
    if metal_seg_finding:
        findings = f"{findings} {metal_seg_finding}"
    if lesion_seg_finding:
        findings = f"{findings} {lesion_seg_finding}"
    if metal_advice:
        risk_advice = f"{risk_advice} {metal_advice}"
    if metal_seg_advice:
        risk_advice = f"{risk_advice} {metal_seg_advice}"
    if lesion_seg_advice:
        risk_advice = f"{risk_advice} {lesion_seg_advice}"

    if clinical_context:
        risk_advice += f"  临床背景：{clinical_context}"

    return {
        "findings":       findings,
        "conclusion":     conclusion,
        "riskAdvice":     risk_advice,
        "confidence":     confidence,
        "label":          label,
        "metalArtifact":  metal_result,
        "metalArtifactSegmentation": metal_seg_result,
        "lesionSegmentation": lesion_seg_result,
        "abnormalRegions": detections,
        "inferenceTimingsMs": {**timings, "total": int((time.perf_counter() - total_started) * 1000)},
        "modelVersion":   model_version,
    }


def _get_minio_client():
    from minio import Minio
    return Minio(
        os.getenv("MINIO_ENDPOINT", "localhost:9000"),
        access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
        secure=os.getenv("MINIO_SECURE", "false").lower() == "true",
    )


def _require_model_outputs(
    metal_result: dict,
    metal_seg_result: dict,
    lesion_seg_result: dict,
) -> None:
    missing = []
    if not metal_result.get("enabled"):
        missing.append("CT_METAL_CLASSIFIER_MODEL")
    if not metal_seg_result.get("enabled"):
        missing.append("CT_METAL_SEGMENTATION_MODEL")
    if not lesion_seg_result.get("enabled"):
        missing.append("CT_LESION_SEGMENTATION_MODEL")
    if missing:
        raise FileNotFoundError(
            "Required CT small model(s) are not available: " + ", ".join(missing)
        )
