from typing import Optional

from pydantic import BaseModel, Field


class ClinicalAssistanceRequest(BaseModel):
    appointment_id: str = Field(alias="appointmentId")
    patient_id: str = Field(alias="patientId")
    chief_complaint: str = Field(default="", alias="chiefComplaint")
    present_illness: str = Field(default="", alias="presentIllness")
    prompt: str = ""

    model_config = {"populate_by_name": True}


class ClinicalSuggestion(BaseModel):
    kind: str
    label: str
    content: str
    source: str = "AI"


class ClinicalKnowledgeSource(BaseModel):
    source_id: str = Field(alias="sourceId")
    source_type: str = Field(default="RULE", alias="sourceType")
    business_id: Optional[str] = Field(default=None, alias="businessId")
    title: str
    content: str
    score: Optional[float] = None

    model_config = {"populate_by_name": True}


class ClinicalAssistanceResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")
    suggestions: list[ClinicalSuggestion]
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    safety_notice: str = Field(
        default="AI 仅生成辅助建议，最终诊断、处方和处置必须由医生人工确认。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
