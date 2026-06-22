from fastapi import APIRouter, HTTPException
from .models import CtAnalysisRequest, TaskDetail, TaskResponse
from .service import get, submit

router = APIRouter(tags=["ct-analysis"])

@router.post("/ct-analysis", response_model=TaskResponse, response_model_by_alias=True, status_code=202)
def create(request: CtAnalysisRequest) -> TaskResponse:
    task_id = submit(request)
    return TaskResponse(taskId=task_id, status="QUEUED")

@router.get("/tasks/{task_id}", response_model=TaskDetail, response_model_by_alias=True)
def task(task_id: str) -> TaskDetail:
    result = get(task_id)
    if result is None:
        raise HTTPException(status_code=404, detail="AI task not found")
    return TaskDetail(**result)
