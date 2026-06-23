from typing import Optional

from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


class ExecutorCandidate(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    specialties: list[str] = Field(default_factory=list)
    current_load: int = Field(default=0, ge=0, alias="currentLoad")
    capacity: int = Field(default=1, ge=1)
    available: bool = True
    location: str
    equipment_ids: list[str] = Field(default_factory=list, alias="equipmentIds")

    model_config = {"populate_by_name": True}


class TriageRequest(BaseModel):
    order_id: str = Field(alias="orderId")
    project_type: str = Field(alias="projectType")
    body_part: Optional[str] = Field(default=None, alias="bodyPart")
    required_specialty: Optional[str] = Field(default=None, alias="requiredSpecialty")
    urgency: str = "ROUTINE"
    candidates: list[ExecutorCandidate]

    model_config = {"populate_by_name": True}


class TriageResponse(BaseModel):
    order_id: str = Field(alias="orderId")
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    location: str
    equipment_id: Optional[str] = Field(default=None, alias="equipmentId")
    score: float
    reasons: list[str]
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")

    model_config = {"populate_by_name": True}
