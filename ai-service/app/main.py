from fastapi import FastAPI
from app.consultation.router import router as consultation_router

app = FastAPI(title="Cloud Brain Medical AI Service")
app.include_router(consultation_router, prefix="/api/ai")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
