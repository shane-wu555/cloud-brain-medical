from fastapi import APIRouter

from .models import DoctorRecommendationRequest, DoctorRecommendationResponse
from .service import recommend_doctors

router = APIRouter(tags=["doctor-recommendations"])


@router.post("/doctor-recommendations", response_model=DoctorRecommendationResponse, response_model_by_alias=True)
def create_doctor_recommendations(request: DoctorRecommendationRequest) -> DoctorRecommendationResponse:
    return recommend_doctors(request)
