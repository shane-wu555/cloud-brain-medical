from fastapi import APIRouter

from .models import ScheduleSuggestionRequest, ScheduleSuggestionResponse
from .service import suggest

router = APIRouter(tags=["schedule-suggestions"])


@router.post("/schedule-suggestions", response_model=ScheduleSuggestionResponse, response_model_by_alias=True)
def create_schedule_suggestions(request: ScheduleSuggestionRequest) -> ScheduleSuggestionResponse:
    return suggest(request)
