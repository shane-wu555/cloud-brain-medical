from uuid import uuid4

from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve
from app.clinical_assistance.models import ClinicalKnowledgeSource

from .models import ReportDraftRequest, ReportDraftResponse


def create_draft(request: ReportDraftRequest) -> ReportDraftResponse:
    config = settings()
    if config.llm_enabled:
        try:
            return _draft_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_draft(request, fallback_used=config.llm_enabled)


def _draft_with_llm(request: ReportDraftRequest, config) -> ReportDraftResponse:
    knowledge_sources = _knowledge_sources(request)
    result = chat_json(
        config,
        system_prompt="""
你是医院医技报告草稿助手。只能生成报告草稿，不能发布正式报告。
请严格输出 JSON 对象，字段为 findings、conclusion、advice。
必须优先参考 providedKnowledgeSources 中的本院医技项目和院内规则。
不得编造未提供的检查所见；如果信息不足，请在 advice 中提示医生补充检查数据。
""",
        user_payload={
            **request.model_dump(by_alias=True),
            "providedKnowledgeSources": [
                source.model_dump(by_alias=True) for source in knowledge_sources
            ],
        },
    )
    payload = extract_json_object(result.content)
    return ReportDraftResponse(
        aiRecordId=f"ai-report-{uuid4()}",
        findings=payload["findings"],
        conclusion=payload["conclusion"],
        advice=payload["advice"],
        knowledgeSources=knowledge_sources,
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _mock_draft(request: ReportDraftRequest, fallback_used: bool = False) -> ReportDraftResponse:
    knowledge_sources = _knowledge_sources(request)
    project = request.item_name or request.report_type
    findings = request.findings or f"{project} 已完成，所见需由执行医生结合原始数据补充。"
    conclusion = request.conclusion or "AI 未形成正式结论，请医技医生复核后填写。"
    source_label = knowledge_sources[0].title if knowledge_sources else "本院规则"
    return ReportDraftResponse(
        aiRecordId=f"ai-report-{uuid4()}",
        findings=findings,
        conclusion=conclusion,
        advice=f"该内容为 AI 报告草稿，需人工修改或确认后才能发布给患者和门诊医生。参考来源：{source_label}。",
        knowledgeSources=knowledge_sources,
        fallbackUsed=fallback_used,
    )


def _knowledge_sources(request: ReportDraftRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        value
        for value in [request.report_type, request.item_name, request.findings, request.conclusion, request.context]
        if value
    )
    return [
        ClinicalKnowledgeSource(
            sourceId=source.source_id,
            sourceType=source.source_type,
            businessId=source.business_id,
            title=source.title,
            content=source.content,
            score=source.score,
        )
        for source in retrieve(query, limit=4)
    ]
