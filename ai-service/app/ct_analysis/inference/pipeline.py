"""
CT 推理主流程（三阶段）：
  1. 下载 + 预处理 DICOM → 切片数组
  2. EfficientNet-B0 分类
  3. 若异常 → YOLOv8 检测框
返回结构化报告字段。
"""

import os
import logging
from typing import Any

from .preprocessing import download_and_load, volume_to_slices
from .classifier    import classify_volume
from .detector      import detect_volume, top_abnormal_slices
from .metal_classifier import classify_metal_artifact
from .metal_segmentation import segment_metal_artifact

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
    "normal":     "未见明确急性颅内出血或梗死征象，请结合临床综合判断。",
    "hemorrhage": "颅内出血可能性大（AI 置信度 {confidence:.0%}），"
                  "建议立即通知临床医生并由检查医生复核确认。",
    "ischemia":   "脑缺血性改变可能（AI 置信度 {confidence:.0%}），"
                  "建议结合 DWI/MRI 进一步评估，由检查医生复核。",
}

RISK_ADVICE = {
    "normal":     "AI 初步筛查未见异常，仍需检查医生确认后方可发布报告。",
    "hemorrhage": "⚠️ 高风险：检测到颅内出血征象，请立即通知值班医生，按急诊流程处理。",
    "ischemia":   "⚠️ 注意：存在脑缺血性改变迹象，建议优先安排 MRI 检查并联系神经内科。",
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
    confidence = float(metal_result.get("confidence", 0.0))
    finding = f"金属伪影评估：{label_cn}（AI置信度 {confidence:.0%}）。"

    if label in {"moderate_metal", "severe_metal"}:
        advice = "图像存在较明显金属伪影，相关区域诊断可信度可能下降，建议结合原始薄层图像或必要时复查。"
    elif label == "small_metal":
        advice = "图像存在轻度金属伪影，建议阅片时关注邻近高密度区域。"
    else:
        advice = "未见明显影响诊断的金属伪影。"
    return finding, advice


def _metal_seg_report_text(seg_result: dict) -> tuple[str, str]:
    if not seg_result.get("enabled"):
        return "", ""
    if not seg_result.get("hasArtifactRegion"):
        return "金属伪影区域分割：未定位到明显疑似区域。", ""

    affected = int(seg_result.get("affectedSlices", 0))
    total = int(seg_result.get("totalSlices", 0))
    fg_ratio = float(seg_result.get("foregroundRatio", 0.0))
    confidence = float(seg_result.get("confidence", 0.0))
    finding = (
        "金属伪影区域分割：已定位到疑似金属/伪影区域，"
        f"累及 {affected}/{total} 层，前景占比 {fg_ratio:.2%}，"
        f"最高置信度 {confidence:.0%}。"
    )
    advice = "请在金属伪影分割提示区域内结合原始图像复核，该区域的病灶判断需谨慎。"
    return finding, advice


def run(object_key: str, order_id: str, clinical_context: str = "") -> dict[str, Any]:
    """
    完整推理流程入口。
    返回:
        findings, conclusion, riskAdvice, confidence,
        abnormalRegions (bboxes), modelVersion
    """
    model_version = os.getenv("CT_MODEL_VERSION", "ct-head-v1.0")

    # ── 1. 下载 + 预处理 ──────────────────────────────────
    minio_client = _get_minio_client()
    log.info(f"[CT推理] 开始下载 objectKey={object_key}")
    hu_volume = download_and_load(object_key, minio_client)
    slices, valid_indices = volume_to_slices(hu_volume)
    log.info(f"[CT推理] 体积 shape={hu_volume.shape}, 有效切片={len(slices)}")

    # ── 2. 分类 ───────────────────────────────────────────
    clf_result  = classify_volume(slices)
    label       = clf_result["label"]
    confidence  = clf_result["confidence"]
    slice_probs = clf_result["slice_probs"]
    metal_result = classify_metal_artifact(slices)
    metal_seg_result = segment_metal_artifact(hu_volume, valid_indices)
    log.info(f"[CT推理] 分类结果: {label} ({confidence:.2%})")

    # ── 3. 检测（仅异常时）────────────────────────────────
    detections: list[dict] = []
    if label != "normal" and slice_probs:
        top_slices = top_abnormal_slices(slice_probs, top_k=5)
        sorted_slices = slices[top_slices] if top_slices else slices[:5]
        sorted_indices = valid_indices[top_slices] if top_slices else valid_indices[:5]
        detections = detect_volume(sorted_slices, sorted_indices)
        log.info(f"[CT推理] 检测框数量: {len(detections)}")

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
    if metal_finding:
        findings = f"{findings} {metal_finding}"
    if metal_seg_finding:
        findings = f"{findings} {metal_seg_finding}"
    if metal_advice:
        risk_advice = f"{risk_advice} {metal_advice}"
    if metal_seg_advice:
        risk_advice = f"{risk_advice} {metal_seg_advice}"

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
        "abnormalRegions": detections,
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
