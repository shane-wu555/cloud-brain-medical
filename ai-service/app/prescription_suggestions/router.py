from fastapi import APIRouter

from .models import PrescriptionSuggestionRequest, PrescriptionSuggestionResponse
from .service import suggest_prescription

router = APIRouter(tags=["prescription-suggestions"])


@router.post("/prescription-suggestions", response_model=PrescriptionSuggestionResponse, response_model_by_alias=True)
def create_prescription_suggestions(request: PrescriptionSuggestionRequest) -> PrescriptionSuggestionResponse:
    return suggest_prescription(request)
