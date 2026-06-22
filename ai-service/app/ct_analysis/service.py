import threading
import time
import uuid
from typing import Any
from .models import CtAnalysisRequest

_tasks: dict[str, dict[str, Any]] = {}
_lock = threading.Lock()

def submit(request: CtAnalysisRequest) -> str:
    task_id = f"ct-{uuid.uuid4()}"
    with _lock:
        _tasks[task_id] = {"taskId": task_id, "status": "QUEUED", "modelVersion": "ct-demo-1.0", "result": None, "error": None}
    threading.Thread(target=_run, args=(task_id, request), daemon=True).start()
    return task_id

def _run(task_id: str, request: CtAnalysisRequest) -> None:
    try:
        with _lock:
            _tasks[task_id]["status"] = "RUNNING"
        time.sleep(0.15)
        result = {
            "findings": "头颅CT平扫示脑实质密度尚均匀，未见明确急性出血征象。",
            "conclusion": "当前样例未检出明确急性颅内出血，请结合临床并由检查医生复核。",
            "riskAdvice": "AI结果仅供辅助，必须由检查医生确认后发布。",
            "abnormalRegions": [],
            "confidence": 0.86,
            "objectKey": request.object_key,
        }
        with _lock:
            _tasks[task_id].update(status="COMPLETED", result=result)
    except Exception as exc:
        with _lock:
            _tasks[task_id].update(status="FAILED", error=str(exc))

def get(task_id: str) -> dict[str, Any] | None:
    with _lock:
        task = _tasks.get(task_id)
        return dict(task) if task else None
