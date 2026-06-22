from fastapi import APIRouter
from .models import ConsultationRequest, ConsultationResponse
from .service import consult

router = APIRouter(tags=["consultation"])


@router.post("/consultations", response_model=ConsultationResponse, response_model_by_alias=True)
def create_consultation(request: ConsultationRequest) -> ConsultationResponse:
    return consult(request)
