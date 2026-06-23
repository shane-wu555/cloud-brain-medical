from pydantic import BaseModel, Field

from app.clinical_assistance.models import ClinicalKnowledgeSource


class ReportDraftRequest(BaseModel):
    order_id: str = Field(alias="orderId")
    report_type: str = Field(default="CHECK", alias="reportType")
    project_name: str = Field(default="", alias="projectName")
    findings: str = ""
    conclusion: str = ""
    context: str = ""

    model_config = {"populate_by_name": True}


class ReportDraftResponse(BaseModel):
    ai_record_id: str = Field(alias="aiRecordId")
    created_by_type: str = Field(default="AI", alias="createdByType")
    requires_human_confirmation: bool = Field(default=True, alias="requiresHumanConfirmation")
    findings: str
    conclusion: str
    advice: str
    knowledge_sources: list[ClinicalKnowledgeSource] = Field(default_factory=list, alias="knowledgeSources")
    provider: str = "mock"
    model: str = "mock"
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    safety_notice: str = Field(
        default="AI 报告只能作为草稿，正式报告必须由对应医技医生确认发布。",
        alias="safetyNotice",
    )

    model_config = {"populate_by_name": True}
