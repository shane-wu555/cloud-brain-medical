from fastapi import APIRouter, HTTPException

from .models import TriageRequest, TriageResponse
from .service import dispatch

router = APIRouter(tags=["triage"])


@router.post("/triage", response_model=TriageResponse, response_model_by_alias=True)
def create_triage(request: TriageRequest) -> TriageResponse:
    try:
        return dispatch(request)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
