from fastapi import FastAPI
from app.consultation.router import router as consultation_router
from app.clinical_assistance.router import router as clinical_assistance_router
from app.triage.router import router as triage_router
from app.ct_analysis.router import router as ct_analysis_router

app = FastAPI(title="Cloud Brain Medical AI Service")
app.include_router(consultation_router, prefix="/api/ai")
app.include_router(clinical_assistance_router, prefix="/api/ai")
app.include_router(triage_router, prefix="/api/ai")
app.include_router(ct_analysis_router, prefix="/api/ai")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
