from pydantic import BaseModel, Field


class ConsultationRequest(BaseModel):
    patient_id: str | None = Field(default=None, alias="patientId")
    description: str = ""
    symptom_tags: list[str] = Field(default_factory=list, alias="symptomTags")


class DoctorRecommendation(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    reason: str

    model_config = {"populate_by_name": True}


class ConsultationResponse(BaseModel):
    summary: str
    risk_level: str = Field(alias="riskLevel")
    recommended_department_id: str = Field(alias="recommendedDepartmentId")
    recommended_department_name: str = Field(alias="recommendedDepartmentName")
    recommended_doctors: list[DoctorRecommendation] = Field(alias="recommendedDoctors")
    suggest_offline_urgent: bool = Field(alias="suggestOfflineUrgent")
    record_draft: str = Field(alias="recordDraft")

    model_config = {"populate_by_name": True}
