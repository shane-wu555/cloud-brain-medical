import json
import logging

from fastapi import APIRouter, HTTPException, Request
from pydantic import ValidationError

from .models import ScheduleSuggestionRequest, ScheduleSuggestionResponse
from .service import suggest

router = APIRouter(tags=["schedule-suggestions"])
logger = logging.getLogger(__name__)


@router.post("/schedule-suggestions", response_model=ScheduleSuggestionResponse, response_model_by_alias=True)
async def create_schedule_suggestions(request: Request) -> ScheduleSuggestionResponse:
    try:
        payload = await request.json()
        if isinstance(payload, str):
            payload = json.loads(payload)
        schedule_request = ScheduleSuggestionRequest.model_validate(payload)
    except json.JSONDecodeError as exc:
        logger.warning("Invalid schedule suggestion JSON body: %s", exc)
        raise HTTPException(status_code=422, detail=f"Invalid JSON body: {exc}") from exc
    except ValidationError as exc:
        logger.warning("Invalid schedule suggestion request: %s; payload=%s", exc.errors(), _payload_preview(payload))
        raise HTTPException(status_code=422, detail=exc.errors()) from exc
    return suggest(schedule_request)


def _payload_preview(payload) -> str:
    try:
        return json.dumps(payload, ensure_ascii=False)[:2000]
    except (TypeError, ValueError):
        return repr(payload)[:2000]
