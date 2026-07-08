import json
import logging
import os
import threading
import uuid
from typing import Any

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.rag import retrieve
from app.report_drafts.models import ReportDraftRequest
from app.report_drafts.service import create_draft

from .models import CtAnalysisRequest

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

        infer_result = _run_inference(request, task_id)
        sources = _knowledge_sources(request)
        draft = create_draft(
            ReportDraftRequest(
                orderId=request.order_id,
                reportType="CHECK",
                itemName=f"{request.body_part} {request.modality}",
                findings=infer_result["findings"],
                conclusion=infer_result["conclusion"],
                context=_report_context_from_inference(request, infer_result),
            )
        )
        result = {
            **infer_result,
            "objectKey": request.object_key,
            "reportDraft": draft.model_dump(by_alias=True),
        }
        with _lock:
            _tasks[task_id].update(
                status="COMPLETED",
                progress=100,
                result=result,
                knowledgeSources=sources,
                modelVersion=infer_result.get("modelVersion", "ct-head-v1.0"),
            )
    except Exception as exc:
        log.exception("CT inference failed task=%s", task_id)
        with _lock:
            _tasks[task_id].update(status="FAILED", progress=100, error=str(exc))


def _run_inference(request: CtAnalysisRequest, task_id: str) -> dict[str, Any]:
    """Run the real CT model pipeline.

    By default this does not fall back to mock output. Demo fallback is only
    allowed when CT_INFERENCE_ALLOW_MOCK=true is set explicitly.
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
        if os.getenv("CT_INFERENCE_ALLOW_MOCK", "false").strip().lower() != "true":
            raise
        log.warning("CT model inference failed, using explicit demo fallback: %s", exc)
        return _mock_inference(request)


def _mock_inference(request: CtAnalysisRequest) -> dict[str, Any]:
    """Explicit demo fallback. Not used unless CT_INFERENCE_ALLOW_MOCK=true."""
    if "fail" in request.object_key.lower():
        raise RuntimeError("Simulated CT inference failure; retry is allowed")
    return {
        "findings": "演示模式：未运行真实 CT 小模型，请勿作为诊断依据。",
        "conclusion": "演示模式未形成诊断性结论，请检查小模型配置后重新提交。",
        "riskAdvice": "当前为显式 demo fallback，正式流程必须由真实小模型和检查医生确认。",
        "confidence": 0.0,
        "label": "demo",
        "metalArtifact": {"enabled": False},
        "metalArtifactSegmentation": {
            "enabled": False,
            "hasArtifactRegion": False,
            "affectedSlices": 0,
            "totalSlices": 0,
            "foregroundRatio": 0.0,
            "confidence": 0.0,
            "topSlices": [],
        },
        "lesionSegmentation": {
            "enabled": False,
            "hasLesionRegion": False,
            "affectedSlices": 0,
            "totalSlices": 0,
            "foregroundRatio": 0.0,
            "confidence": 0.0,
            "topSlices": [],
        },
        "abnormalRegions": [],
        "modelVersion": "ct-demo-1.0",
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
            raise ValueError("Only failed or completed tasks can be retried")
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
        "modelVersion": None,
        "retryCount": retry_count,
        "createdByType": "AI",
        "requiresHumanConfirmation": True,
        "knowledgeSources": _knowledge_sources(request),
        "result": None,
        "error": None,
        "_request": request,
    }


def _report_context_from_inference(request: CtAnalysisRequest, result: dict[str, Any]) -> str:
    model_basis = {
        "modelVersion": result.get("modelVersion"),
        "label": result.get("label"),
        "confidence": result.get("confidence"),
        "riskAdvice": result.get("riskAdvice"),
        "abnormalRegions": result.get("abnormalRegions", []),
        "metalArtifact": result.get("metalArtifact"),
        "metalArtifactSegmentation": result.get("metalArtifactSegmentation"),
        "lesionSegmentation": result.get("lesionSegmentation"),
    }
    return "\n".join(
        [
            f"检查类型：{request.modality}",
            f"检查部位：{request.body_part}",
            f"临床背景：{request.clinical_context or '未提供'}",
            "小模型结构化诊断结果如下，报告草稿必须以这些结果为依据，不得新增未提供的影像征象或诊断：",
            json.dumps(model_basis, ensure_ascii=False, separators=(",", ":")),
        ]
    )


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
