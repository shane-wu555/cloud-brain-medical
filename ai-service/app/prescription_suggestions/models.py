from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


class PrescriptionSuggestionRequest(BaseModel):
    appointment_id: str = Field(alias="appointmentId")
    patient_id: str = Field(alias="patientId")
    diagnosis: str = ""
    chief_complaint: str = Field(default="", alias="chiefComplaint")
    allergy_history: str = Field(default="", alias="allergyHistory")
    medication_history: str = Field(default="", alias="medicationHistory")
    prompt: str = ""

    model_config = {"populate_by_name": True}


class MedicationSuggestion(BaseModel):
    drug_name: str = Field(alias="drugName")
    dosage: str
    usage: str
    frequency: str
    days: int = 1
    note: str
    source: str

    model_config = {"populate_by_name": True}


class PrescriptionSuggestionResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")
    suggestions: list[MedicationSuggestion]
    warnings: list[str] = Field(default_factory=list)
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    safety_notice: str = Field(
        default="AI 仅生成用药建议草稿，正式处方必须由门诊医生确认，缴费后才可进入药房发药。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
