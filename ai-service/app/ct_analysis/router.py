from fastapi import APIRouter, HTTPException
from .models import CtAnalysisRequest, TaskDetail, TaskResponse
from .service import get, retry, submit

router = APIRouter(tags=["ct-analysis"])

@router.post("/ct-analysis", response_model=TaskResponse, response_model_by_alias=True, status_code=202)
def create(request: CtAnalysisRequest) -> TaskResponse:
    task_id = submit(request)
    return TaskResponse(taskId=task_id, status="QUEUED", progress=0)

@router.get("/tasks/{task_id}", response_model=TaskDetail, response_model_by_alias=True)
def task(task_id: str) -> TaskDetail:
    result = get(task_id)
    if result is None:
        raise HTTPException(status_code=404, detail="AI task not found")
    return TaskDetail(**result)


@router.post("/tasks/{task_id}/retry", response_model=TaskResponse, response_model_by_alias=True, status_code=202)
def retry_task(task_id: str) -> TaskResponse:
    try:
        retried_id = retry(task_id)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return TaskResponse(taskId=retried_id, status="QUEUED", progress=0)
