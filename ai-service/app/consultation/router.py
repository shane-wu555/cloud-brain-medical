from fastapi import APIRouter
from .models import ConsultationMessageRequest, ConsultationRequest, ConsultationResponse
from .service import consult, continue_consultation

router = APIRouter(tags=["consultation"])


@router.post("/consultations", response_model=ConsultationResponse, response_model_by_alias=True)
def create_consultation(request: ConsultationRequest) -> ConsultationResponse:
    return consult(request)


@router.post("/consultations/{consultation_id}/messages", response_model=ConsultationResponse, response_model_by_alias=True)
def create_consultation_message(consultation_id: str, request: ConsultationMessageRequest) -> ConsultationResponse:
    return continue_consultation(consultation_id, request)
