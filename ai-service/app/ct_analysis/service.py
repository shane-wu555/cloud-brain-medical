import threading
import time
import uuid
from typing import Any
from .models import CtAnalysisRequest
from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.rag import retrieve
from app.report_drafts.models import ReportDraftRequest
from app.report_drafts.service import create_draft

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
            _tasks[task_id].update(status="RUNNING", progress=20, error=None)
        time.sleep(0.15)
        with _lock:
            _tasks[task_id]["progress"] = 70
        if "fail" in request.object_key.lower():
            raise RuntimeError("模拟 CT 推理失败，可通过 retry 重新提交")
        sources = _knowledge_sources(request)
        result = {
            "findings": "头颅CT平扫示脑实质密度尚均匀，未见明确急性出血征象。",
            "conclusion": "当前样例未检出明确急性颅内出血，请结合临床并由检查医生复核。",
            "riskAdvice": f"AI结果仅供辅助，必须由检查医生确认后发布。参考来源：{sources[0].title if sources else '本院规则'}。",
            "abnormalRegions": [],
            "confidence": 0.86,
            "objectKey": request.object_key,
            "reportDraft": create_draft(
                ReportDraftRequest(
                    orderId=request.order_id,
                    reportType="CHECK",
                    projectName="头部 CT",
                    findings="头颅CT平扫示脑实质密度尚均匀，未见明确急性出血征象。",
                    conclusion="当前样例未检出明确急性颅内出血，请结合临床并由检查医生复核。",
                    context=request.clinical_context,
                )
            ).model_dump(by_alias=True),
        }
        with _lock:
            _tasks[task_id].update(status="COMPLETED", progress=100, result=result, knowledgeSources=sources)
    except Exception as exc:
        with _lock:
            _tasks[task_id].update(status="FAILED", progress=100, error=str(exc))

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
