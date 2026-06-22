from typing import Any
from pydantic import BaseModel, Field

class CtAnalysisRequest(BaseModel):
    order_id: str = Field(alias="orderId")
    object_key: str = Field(alias="objectKey")
    modality: str = "CT"
    body_part: str = Field(default="HEAD", alias="bodyPart")
    model_config = {"populate_by_name": True}

class TaskResponse(BaseModel):
    task_id: str = Field(alias="taskId")
    status: str
    model_config = {"populate_by_name": True}

class TaskDetail(TaskResponse):
    model_version: str | None = Field(default=None, alias="modelVersion")
    result: dict[str, Any] | None = None
    error: str | None = None
