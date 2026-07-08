from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from app.consultation.router import router as consultation_router
from app.clinical_assistance.router import router as clinical_assistance_router
from app.triage.router import router as triage_router
from app.ct_analysis.router import router as ct_analysis_router
from app.ct_analysis.dicom_conversion import router as dicom_conversion_router
from app.schedule_suggestions.router import router as schedule_suggestions_router
from app.report_drafts.router import router as report_drafts_router
from app.knowledge.router import router as knowledge_router
from app.prescription_suggestions.router import router as prescription_suggestions_router
from app.doctor_recommendations.router import router as doctor_recommendations_router
from app.core.llm import LlmError

app = FastAPI(title="Cloud Brain Medical AI Service")
app.include_router(consultation_router, prefix="/api/ai")
app.include_router(clinical_assistance_router, prefix="/api/ai")
app.include_router(triage_router, prefix="/api/ai")
app.include_router(ct_analysis_router, prefix="/api/ai")
app.include_router(dicom_conversion_router, prefix="/api/ai")
app.include_router(schedule_suggestions_router, prefix="/api/ai")
app.include_router(report_drafts_router, prefix="/api/ai")
app.include_router(knowledge_router, prefix="/api/ai")
app.include_router(prescription_suggestions_router, prefix="/api/ai")
app.include_router(doctor_recommendations_router, prefix="/api/ai")


@app.exception_handler(LlmError)
async def llm_error_handler(_, exc: LlmError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.kind,
            "message": str(exc),
            "providerStatus": exc.provider_status,
            "fallbackAvailable": True,
            "hint": "Set AI_ALLOW_FALLBACK=true for demos, or check AI_OPENAI_BASE_URL, AI_OPENAI_MODEL, and AI_OPENAI_API_KEY.",
        },
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(_, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=400,
        content={
            "code": "REQUEST_VALIDATION_ERROR",
            "message": "Request payload failed validation.",
            "detail": exc.errors(),
        },
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
