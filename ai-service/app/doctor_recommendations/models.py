from typing import Optional

from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


class DoctorCandidate(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    department_id: str = Field(alias="departmentId")
    department_name: str = Field(alias="departmentName")
    title: str = ""
    specialties: list[str] = Field(default_factory=list)
    available: bool = True
    remaining_slots: int = Field(default=0, alias="remainingSlots")
    next_available_time: Optional[str] = Field(default=None, alias="nextAvailableTime")

    model_config = {"populate_by_name": True}


class DoctorRecommendationRequest(BaseModel):
    patient_id: Optional[str] = Field(default=None, alias="patientId")
    symptoms: str = ""
    symptom_tags: list[str] = Field(default_factory=list, alias="symptomTags")
    risk_level: str = Field(default="LOW", alias="riskLevel")
    preferred_department_id: Optional[str] = Field(default=None, alias="preferredDepartmentId")
    candidates: list[DoctorCandidate] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class DoctorRecommendationItem(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    department_id: str = Field(alias="departmentId")
    department_name: str = Field(alias="departmentName")
    score: float
    reason: str
    source: str

    model_config = {"populate_by_name": True}


class DoctorRecommendationResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    created_by_type: str = Field(default="AI", alias="createdByType")
    recommendations: list[DoctorRecommendationItem]
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    safety_notice: str = Field(
        default="AI 医生推荐仅供患者选择挂号参考，最终挂号以实时排班和号源为准。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
