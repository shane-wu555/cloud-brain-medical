from typing import Optional

from pydantic import BaseModel, Field


class ConsultationRequest(BaseModel):
    patient_id: Optional[str] = Field(default=None, alias="patientId")
    description: str = ""
    symptom_tags: list[str] = Field(default_factory=list, alias="symptomTags")

    model_config = {"populate_by_name": True}


class ConsultationMessageRequest(BaseModel):
    message: str
    symptom_tags: list[str] = Field(default_factory=list, alias="symptomTags")

    model_config = {"populate_by_name": True}


class DoctorRecommendation(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    reason: str

    model_config = {"populate_by_name": True}


class ConsultationResponse(BaseModel):
    consultation_id: Optional[str] = Field(default=None, alias="consultationId")
    ai_record_id: Optional[str] = Field(default=None, alias="aiRecordId")
    summary: str
    risk_level: str = Field(alias="riskLevel")
    recommended_department_id: str = Field(alias="recommendedDepartmentId")
    recommended_department_name: str = Field(alias="recommendedDepartmentName")
    recommended_doctors: list[DoctorRecommendation] = Field(alias="recommendedDoctors")
    suggest_offline_urgent: bool = Field(alias="suggestOfflineUrgent")
    needs_follow_up: bool = Field(default=False, alias="needsFollowUp")
    follow_up_questions: list[str] = Field(default_factory=list, alias="followUpQuestions")
    record_draft: str = Field(alias="recordDraft")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    knowledge_sources: list[dict] = Field(default_factory=list, alias="knowledgeSources")
    safety_notice: str = Field(
        default="AI 问诊结果仅用于就诊前信息整理，不能替代医生诊断；急危症状请立即线下急诊。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
