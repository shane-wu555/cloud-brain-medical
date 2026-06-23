from fastapi import APIRouter

from .models import ReportDraftRequest, ReportDraftResponse
from .service import create_draft

router = APIRouter(tags=["report-drafts"])


@router.post("/report-drafts", response_model=ReportDraftResponse, response_model_by_alias=True)
def create_report_draft(request: ReportDraftRequest) -> ReportDraftResponse:
    return create_draft(request)
