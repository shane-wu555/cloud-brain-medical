from typing import Any, Optional

from pydantic import BaseModel, Field, field_validator

from app.clinical_assistance.models import ClinicalKnowledgeSource


def _string_or_empty(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def _int_or_default(value: Any, default: int) -> int:
    if value is None or value == "":
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _normalize_period(value: Any) -> str:
    text = _string_or_empty(value).strip()
    upper_text = text.upper()
    if upper_text == "MORNING":
        return "\u4e0a\u5348"
    if upper_text == "AFTERNOON":
        return "\u4e0b\u5348"
    if upper_text in {"ALL_DAY", "FULL_DAY", "DAY"}:
        return "\u5168\u5929"
    return text


def _string_list_or_empty(value: Any) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None and str(item).strip()]


def _dict_list_or_empty(value: Any) -> list[dict]:
    if value is None:
        return []
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


class DoctorCandidate(BaseModel):
    doctor_id: str = Field(default="", alias="doctorId")
    doctor_name: str = Field(default="", alias="doctorName")
    department_id: str = Field(default="", alias="departmentId")
    specialty: str = ""
    weekly_capacity: int = Field(default=40, alias="weeklyCapacity")
    leave_dates: list[str] = Field(default_factory=list, alias="leaveDates")
    surgery_dates: list[str] = Field(default_factory=list, alias="surgeryDates")
    unavailable_slots: list[dict] = Field(default_factory=list, alias="unavailableSlots")

    @field_validator("doctor_id", "doctor_name", "department_id", "specialty", mode="before")
    @classmethod
    def default_string(cls, value: Any) -> str:
        return _string_or_empty(value)

    @field_validator("weekly_capacity", mode="before")
    @classmethod
    def default_weekly_capacity(cls, value: Any) -> int:
        return _int_or_default(value, 40)

    @field_validator("leave_dates", "surgery_dates", mode="before")
    @classmethod
    def default_string_list(cls, value: Any) -> list[str]:
        return _string_list_or_empty(value)

    @field_validator("unavailable_slots", mode="before")
    @classmethod
    def default_slot_list(cls, value: Any) -> list[dict]:
        return _dict_list_or_empty(value)

    model_config = {"populate_by_name": True}


class ScheduleDemand(BaseModel):
    department_id: str = Field(default="", alias="departmentId")
    work_date: str = Field(default="", alias="workDate")
    period: str = ""
    expected_visits: int = Field(default=20, alias="expectedVisits")
    risk_level: str = Field(default="MEDIUM", alias="riskLevel")
    historical_visits: Optional[int] = Field(default=None, alias="historicalVisits")

    @field_validator("department_id", "work_date", "risk_level", mode="before")
    @classmethod
    def default_string(cls, value: Any) -> str:
        return _string_or_empty(value)

    @field_validator("period", mode="before")
    @classmethod
    def default_period(cls, value: Any) -> str:
        return _normalize_period(value)

    @field_validator("expected_visits", mode="before")
    @classmethod
    def default_expected_visits(cls, value: Any) -> int:
        return _int_or_default(value, 20)

    @field_validator("historical_visits", mode="before")
    @classmethod
    def default_historical_visits(cls, value: Any) -> Optional[int]:
        if value is None or value == "":
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    model_config = {"populate_by_name": True}


class ScheduleSuggestionRequest(BaseModel):
    candidates: list[DoctorCandidate] = Field(default_factory=list)
    demands: list[ScheduleDemand] = Field(default_factory=list)

    @field_validator("candidates", "demands", mode="before")
    @classmethod
    def default_list(cls, value: Any) -> list:
        return [] if value is None else value

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
