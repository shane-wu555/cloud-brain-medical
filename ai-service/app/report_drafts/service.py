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
不得编造未提供的检查所见、症状、疾病、诊断或检查结果。
如果 reportType 为 LAB，findings 和 conclusion 必须以用户提供的真实检验明细和当前报告为准；advice 只能围绕已提供的异常指标、参考范围和报告结论提出复核/随访建议。
如果信息不足，请在 advice 中提示医生结合临床资料判断，不要补充不存在的数据。
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
    advice = f"该内容为 AI 报告草稿，需人工修改或确认后才能发布给患者和门诊医生。参考来源：{source_label}。"
    if request.report_type.upper() == "LAB":
        abnormal_lines = [
            line.strip()
            for line in request.context.splitlines()
            if "提示=" in line and "提示=正常" not in line
        ]
        if abnormal_lines:
            items = "；".join(abnormal_lines[:6])
            advice = f"请结合临床表现复核以下异常检验指标：{items}。必要时建议复查相关指标或进一步评估，最终处理由医生结合病情决定。"
        elif request.findings or request.conclusion:
            advice = "当前已提供的检验明细未见明确异常提示；如症状持续或临床不符，建议结合病情复查或补充相关检查。"
    return ReportDraftResponse(
        aiRecordId=f"ai-report-{uuid4()}",
        findings=findings,
        conclusion=conclusion,
        advice=advice,
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
