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


class ClinicalAssistanceResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")
    suggestions: list[ClinicalSuggestion]

    model_config = {"populate_by_name": True}
