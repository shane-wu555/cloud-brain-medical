from typing import Any
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part[:1].upper() + part[1:] for part in rest)


CAMEL_ALIAS_CONFIG = ConfigDict(
    alias_generator=to_camel,
    populate_by_name=True,
)


class CtAnalysisRequest(BaseModel):
    order_id: str
    object_key: str
    modality: str = "CT"
    body_part: str = "HEAD"
    clinical_context: str = ""
    model_config = CAMEL_ALIAS_CONFIG

class TaskResponse(BaseModel):
    task_id: str
    status: str
    progress: int = 0
    model_config = CAMEL_ALIAS_CONFIG

class TaskDetail(TaskResponse):
    model_version: Optional[str] = None
    retry_count: int = 0
    created_by_type: str = "AI"
    requires_human_confirmation: bool = True
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list)
    result: Optional[dict[str, Any]] = None
    error: Optional[str] = None
