from typing import Any
from typing import Optional

from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource

class CtAnalysisRequest(BaseModel):
    order_id: str = Field(alias="orderId")
    object_key: str = Field(alias="objectKey")
    modality: str = "CT"
    body_part: str = Field(default="HEAD", alias="bodyPart")
    clinical_context: str = Field(default="", alias="clinicalContext")
    model_config = {"populate_by_name": True}

class TaskResponse(BaseModel):
    task_id: str = Field(alias="taskId")
    status: str
    progress: int = 0
    model_config = {"populate_by_name": True}

class TaskDetail(TaskResponse):
    model_version: Optional[str] = Field(default=None, alias="modelVersion")
    retry_count: int = Field(default=0, alias="retryCount")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    result: Optional[dict[str, Any]] = None
    error: Optional[str] = None
