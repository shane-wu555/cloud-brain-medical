from fastapi import FastAPI

app = FastAPI(title="Cloud Brain Medical AI Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}

