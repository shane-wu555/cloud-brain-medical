from fastapi import APIRouter

from .models import ClinicalAssistanceRequest, ClinicalAssistanceResponse
from .service import assist

router = APIRouter(tags=["clinical-assistance"])


@router.post("/clinical-assistance", response_model=ClinicalAssistanceResponse, response_model_by_alias=True)
def create_assistance(request: ClinicalAssistanceRequest) -> ClinicalAssistanceResponse:
    return assist(request)
