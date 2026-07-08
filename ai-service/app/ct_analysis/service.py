import json
import logging
import os
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor, TimeoutError
from typing import Any

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.rag import retrieve

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


def _run(task_id: str, request: CtAnalysisRequest) -> None:
    try:
        with _lock:
            _tasks[task_id].update(status="RUNNING", progress=10, error=None)

        infer_result = _run_inference(request, task_id)
        sources = _knowledge_sources_safe(request)
        result = {
            **infer_result,
            "objectKey": request.object_key,
            "reportDraft": _report_draft_from_inference(request, infer_result),
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
    """Run the real CT model pipeline unless explicit demo fallback is enabled."""
    if _mock_allowed():
        return _mock_inference(request)

    from .inference.pipeline import run as ml_run

    def _progress(pct: int) -> None:
        with _lock:
            _tasks[task_id]["progress"] = pct

    _progress(20)
    timeout_seconds = float(os.getenv("CT_INFERENCE_TIMEOUT_SECONDS", "300"))
    executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix=f"{task_id}-worker")
    future = executor.submit(
        ml_run,
        object_key=request.object_key,
        order_id=request.order_id,
        clinical_context=request.clinical_context,
    )
    try:
        result = future.result(timeout=timeout_seconds)
    except TimeoutError as exc:
        future.cancel()
        raise TimeoutError(f"CT inference timed out after {timeout_seconds:.0f}s") from exc
    finally:
        executor.shutdown(wait=False, cancel_futures=True)
    _progress(90)
    return result


def _mock_inference(request: CtAnalysisRequest) -> dict[str, Any]:
    """Explicit demo fallback. Not used unless CT_INFERENCE_ALLOW_MOCK=true."""
    if "fail" in request.object_key.lower():
        raise RuntimeError("Simulated CT inference failure; retry is allowed")
    return {
        "findings": "Demo fallback: real CT small models were not run. Do not use this as a diagnostic basis.",
        "conclusion": "Demo fallback did not produce a diagnostic conclusion. Configure the CT small models and resubmit.",
        "riskAdvice": "This is an explicit demo fallback. Formal workflow must be confirmed by real model output and a checking doctor.",
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
        "reportDraft": _report_draft_from_inference(request, {}),
        "modelVersion": "ct-demo-1.0",
    }


def _mock_allowed() -> bool:
    return os.getenv("CT_INFERENCE_ALLOW_MOCK", "false").strip().lower() == "true"


def _task(task_id: str, request: CtAnalysisRequest, retry_count: int = 0) -> dict[str, Any]:
    return {
        "taskId": task_id,
        "status": "QUEUED",
        "progress": 0,
        "modelVersion": None,
        "retryCount": retry_count,
        "createdByType": "AI",
        "requiresHumanConfirmation": True,
        "knowledgeSources": [],
        "result": None,
        "error": None,
        "_request": request,
    }


def _report_draft_from_inference(request: CtAnalysisRequest, result: dict[str, Any]) -> dict[str, Any]:
    return {
        "createdByType": "AI",
        "requiresHumanConfirmation": True,
        "findings": result.get("findings", ""),
        "conclusion": result.get("conclusion", ""),
        "advice": result.get("riskAdvice", ""),
        "context": _report_context_from_inference(request, result),
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
            f"Exam type: {request.modality}",
            f"Body part: {request.body_part}",
            f"Clinical context: {request.clinical_context or 'not provided'}",
            "Structured CT small-model output follows. The report draft must be based on these findings only.",
            json.dumps(model_basis, ensure_ascii=False, separators=(",", ":")),
        ]
    )


def _knowledge_sources(request: CtAnalysisRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join([request.modality, request.body_part, request.clinical_context, "CT imaging report emergency risk"])
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


def _knowledge_sources_safe(request: CtAnalysisRequest) -> list[ClinicalKnowledgeSource]:
    try:
        return _knowledge_sources(request)
    except Exception as exc:
        log.warning("CT RAG retrieval skipped: %s", exc)
        return []
