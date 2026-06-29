from typing import Optional

from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


class DoctorCandidate(BaseModel):
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    department_id: str = Field(alias="departmentId")
    specialty: str = ""
    weekly_capacity: int = Field(default=40, alias="weeklyCapacity")
    leave_dates: list[str] = Field(default_factory=list, alias="leaveDates")
    surgery_dates: list[str] = Field(default_factory=list, alias="surgeryDates")

    model_config = {"populate_by_name": True}


class ScheduleDemand(BaseModel):
    department_id: str = Field(alias="departmentId")
    work_date: str = Field(alias="workDate")
    period: str
    expected_visits: int = Field(default=20, alias="expectedVisits")
    risk_level: str = Field(default="MEDIUM", alias="riskLevel")
    historical_visits: Optional[int] = Field(default=None, alias="historicalVisits")

    model_config = {"populate_by_name": True}


class ScheduleSuggestionRequest(BaseModel):
    candidates: list[DoctorCandidate]
    demands: list[ScheduleDemand]

    model_config = {"populate_by_name": True}


class ScheduleSuggestion(BaseModel):
    suggestion_id: str = Field(alias="suggestionId")
    doctor_id: str = Field(alias="doctorId")
    doctor_name: str = Field(alias="doctorName")
    department_id: str = Field(alias="departmentId")
    work_date: str = Field(alias="workDate")
    period: str
    capacity: int
    reason: str
    requires_admin_confirmation: bool = Field(default=True, alias="requiresAdminConfirmation")

    model_config = {"populate_by_name": True}


class ScheduleSuggestionResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    suggestions: list[ScheduleSuggestion]
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    safety_notice: str = Field(
        default="AI 排班仅为建议，必须由管理员确认后才能发布并同步号源。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
