import json
import logging
from typing import Any

from fastapi import APIRouter, HTTPException, Request

from .models import CtAnalysisRequest, TaskDetail, TaskResponse
from .service import get, retry, submit

log = logging.getLogger(__name__)
router = APIRouter(tags=["ct-analysis"])

@router.post("/ct-analysis", status_code=202)
async def create(http_request: Request) -> dict[str, Any]:
    payload = await _read_json_object(http_request)
    request = CtAnalysisRequest.model_validate(_normalize_ct_payload(payload))
    task_id = submit(request)
    return TaskResponse(task_id=task_id, status="QUEUED", progress=0).model_dump(by_alias=True)

@router.get("/tasks/{task_id}")
def task(task_id: str) -> dict[str, Any]:
    result = get(task_id)
    if result is None:
        raise HTTPException(status_code=404, detail="AI task not found")
    return TaskDetail.model_validate(result).model_dump(by_alias=True)


@router.post("/tasks/{task_id}/retry", status_code=202)
def retry_task(task_id: str) -> dict[str, Any]:
    try:
        retried_id = retry(task_id)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return TaskResponse(task_id=retried_id, status="QUEUED", progress=0).model_dump(by_alias=True)


async def _read_json_object(request: Request) -> dict[str, Any]:
    raw = await request.body()
    if not raw:
        debug = _request_debug(request, len(raw))
        log.warning("Empty CT analysis request body: %s", debug)
        raise HTTPException(
            status_code=400,
            detail={
                "message": "CT analysis request body is empty",
                "hint": (
                    "Frontend code should call /api/medical-orders/{orderId}/ct-analysis. "
                    "Only medical-order-service should call /api/ai/ct-analysis, and it must send "
                    "orderId/objectKey JSON."
                ),
                **debug,
            },
        )
    try:
        payload = json.loads(raw.decode("utf-8"))
    except UnicodeDecodeError as exc:
        debug = _request_debug(request, len(raw))
        log.warning("Non-UTF8 CT analysis request body: %s", debug)
        raise HTTPException(
            status_code=400,
            detail={"message": "CT analysis request body must be UTF-8 JSON", **debug},
        ) from exc
    except json.JSONDecodeError as exc:
        debug = _request_debug(request, len(raw))
        log.warning("Invalid CT analysis JSON body: debug=%s preview=%s", debug, raw[:256])
        raise HTTPException(
            status_code=400,
            detail={"message": f"CT analysis request body is not valid JSON: {exc.msg}", **debug},
        ) from exc
    if not isinstance(payload, dict):
        raise HTTPException(status_code=400, detail="CT analysis request body must be a JSON object")
    return payload


def _normalize_ct_payload(payload: dict[str, Any]) -> dict[str, Any]:
    if "attachmentId" in payload and not any(key in payload for key in ("objectKey", "object_key")):
        keys = ", ".join(sorted(payload.keys()))
        log.warning("Frontend-style CT payload sent to AI service directly, received=%s", keys or "(none)")
        raise HTTPException(
            status_code=400,
            detail=(
                "CT analysis AI endpoint requires objectKey. "
                "Do not call /api/ai/ct-analysis from the frontend with attachmentId; "
                "call /api/medical-orders/{orderId}/ct-analysis instead. "
                f"Received fields: {keys or '(none)'}"
            ),
        )
    normalized = {
        "order_id": _first_text(payload, "orderId", "order_id"),
        "object_key": _first_text(payload, "objectKey", "object_key"),
        "modality": _first_text(payload, "modality") or "CT",
        "body_part": _first_text(payload, "bodyPart", "body_part") or "HEAD",
        "clinical_context": _first_text(payload, "clinicalContext", "clinical_context") or "",
    }
    missing = [name for name in ("order_id", "object_key") if not normalized[name]]
    if missing:
        keys = ", ".join(sorted(payload.keys()))
        log.warning("Invalid CT analysis payload, missing=%s, received=%s", missing, keys or "(none)")
        raise HTTPException(
            status_code=400,
            detail=(
                f"CT analysis request missing required field(s): {', '.join(missing)}. "
                f"Received fields: {keys or '(none)'}"
            ),
        )
    return normalized


def _first_text(payload: dict[str, Any], *keys: str) -> str:
    for key in keys:
        value = payload.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return ""


def _request_debug(request: Request, body_bytes: int) -> dict[str, Any]:
    headers = request.headers
    client = request.client
    return {
        "caller": headers.get("x-cloudbrain-caller", ""),
        "declaredBodyBytes": headers.get("x-cloudbrain-request-body-bytes", ""),
        "actualBodyBytes": body_bytes,
        "contentLength": headers.get("content-length", ""),
        "contentType": headers.get("content-type", ""),
        "userAgent": headers.get("user-agent", ""),
        "client": f"{client.host}:{client.port}" if client else "",
    }
