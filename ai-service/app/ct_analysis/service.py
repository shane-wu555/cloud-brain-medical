import logging
import os
import threading
import uuid
from typing import Any
from .models import CtAnalysisRequest
from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.rag import retrieve
from app.report_drafts.models import ReportDraftRequest
from app.report_drafts.service import create_draft

log = logging.getLogger(__name__)

_tasks: dict[str, dict[str, Any]] = {}
_lock = threading.Lock()


def submit(request: CtAnalysisRequest) -> str:
    task_id = f"ct-{uuid.uuid4()}"
    with _lock:
        _tasks[task_id] = _task(task_id, request)
    threading.Thread(target=_run, args=(task_id, request), daemon=True).start()
    return task_id


def _run(task_id: str, request: CtAnalysisRequest) -> None:
    try:
        with _lock:
            _tasks[task_id].update(status="RUNNING", progress=10, error=None)

        # ── 真实 ML 推理（有模型文件时走此路径）────────────────
        infer_result = _run_inference(request, task_id)

        sources = _knowledge_sources(request)
        result = {
            **infer_result,
            "objectKey": request.object_key,
            "reportDraft": create_draft(
                ReportDraftRequest(
                    orderId=request.order_id,
                    reportType="CHECK",
                    projectName="头部 CT",
                    findings=infer_result["findings"],
                    conclusion=infer_result["conclusion"],
                    context=request.clinical_context,
                )
            ).model_dump(by_alias=True),
        }
        with _lock:
            _tasks[task_id].update(
                status="COMPLETED", progress=100,
                result=result, knowledgeSources=sources,
                modelVersion=infer_result.get("modelVersion", "ct-head-v1.0"),
            )
    except Exception as exc:
        log.exception(f"CT 推理失败 task={task_id}")
        with _lock:
            _tasks[task_id].update(status="FAILED", progress=100, error=str(exc))


def _run_inference(request: CtAnalysisRequest, task_id: str) -> dict[str, Any]:
    """
    调用 inference/pipeline.py 中的真实模型推理。
    若模型文件不存在（开发阶段），自动降级为 mock 结果。
    """
    try:
        from .inference.pipeline import run as ml_run

        def _progress(pct: int) -> None:
            with _lock:
                _tasks[task_id]["progress"] = pct

        _progress(20)
        result = ml_run(
            object_key=request.object_key,
            order_id=request.order_id,
            clinical_context=request.clinical_context,
        )
        _progress(90)
        return result

    except Exception as exc:
        # 模型文件缺失（FileNotFoundError）或推理失败时降级
        if os.getenv("CT_INFERENCE_ALLOW_MOCK", "true").lower() != "true":
            raise
        log.warning(f"ML 推理失败，降级为 mock: {exc}")
        return _mock_inference(request)

def _mock_inference(request: CtAnalysisRequest) -> dict[str, Any]:
    """开发/测试阶段的 mock 结果（模型未部署时使用）"""
    if "fail" in request.object_key.lower():
        raise RuntimeError("模拟 CT 推理失败，可通过 retry 重新提交")
    return {
        "findings":        "头颅CT平扫示脑实质密度尚均匀，未见明确急性出血征象。（mock）",
        "conclusion":      "当前样例未检出明确急性颅内出血，请结合临床并由检查医生复核。",
        "riskAdvice":      "AI结果仅供辅助，必须由检查医生确认后发布。（mock 模式：模型未部署）",
        "confidence":      0.86,
        "label":           "normal",
        "metalArtifact":   {"enabled": False},
        "metalArtifactSegmentation": {
            "enabled": False,
            "hasArtifactRegion": False,
            "affectedSlices": 0,
            "totalSlices": 0,
            "foregroundRatio": 0.0,
            "confidence": 0.0,
            "topSlices": [],
        },
        "abnormalRegions": [],
        "modelVersion":    "ct-demo-1.0",
    }


def get(task_id: str) -> dict[str, Any] | None:
    with _lock:
        task = _tasks.get(task_id)
        if not task:
            return None
        view = dict(task)
        view.pop("_request", None)
        return view


def retry(task_id: str) -> str:
    with _lock:
        task = _tasks.get(task_id)
        if task is None:
            raise ValueError("AI task not found")
        if task["status"] not in {"FAILED", "COMPLETED"}:
            raise ValueError("仅失败或已完成任务允许重试")
        request = task["_request"]
        retry_count = int(task.get("retryCount", 0)) + 1
        _tasks[task_id] = _task(task_id, request, retry_count=retry_count)
    threading.Thread(target=_run, args=(task_id, request), daemon=True).start()
    return task_id


def _task(task_id: str, request: CtAnalysisRequest, retry_count: int = 0) -> dict[str, Any]:
    return {
        "taskId": task_id,
        "status": "QUEUED",
        "progress": 0,
        "modelVersion": "ct-demo-1.0",
        "retryCount": retry_count,
        "createdByType": "AI",
        "requiresHumanConfirmation": True,
        "knowledgeSources": _knowledge_sources(request),
        "result": None,
        "error": None,
        "_request": request,
    }


def _knowledge_sources(request: CtAnalysisRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join([request.modality, request.body_part, request.clinical_context, "CT 影像 报告 急诊 风险"])
    return [
        ClinicalKnowledgeSource(
            sourceId=source.source_id,
            sourceType=source.source_type,
            businessId=source.business_id,
            title=source.title,
            content=source.content,
            score=source.score,
        )
        for source in retrieve(query, limit=4)
    ]
