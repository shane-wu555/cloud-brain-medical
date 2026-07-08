from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ReportDraftRequest, ReportDraftResponse


REPORT_DRAFT_SYSTEM_PROMPT = """
你是医院医技报告草稿助手，只能生成“草稿”和“后续建议”，不能发布正式报告。
你必须严格输出 JSON 对象，字段只能包含 findings、conclusion、advice。

安全规则：
1. 只能依据用户提供的当前报告所见、当前报告结论、检验明细、病理材料、影像模型结果和 providedKnowledgeSources 生成内容。
2. 不得编造未提供的症状、疾病、检查结果、影像征象、检验数值、病理诊断或治疗方案。
3. 如果输入信息不足，必须在对应字段说明“信息不足，需要医技医生结合原始数据补充/复核”，不能用常见模板填充。
4. reportType=LAB 时，findings 和 conclusion 必须以真实检验明细、当前报告和病理诊断为准；advice 只能围绕已提供的异常指标、参考范围、报告结论提出复核/随访建议。
5. reportType=CHECK 时，如果没有影像模型结果或医生填写的所见，不得声称“未见异常”“已完成检查”等实质性所见。
6. 建议必须保持审慎，最终结论由对应医技医生确认。
"""


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
            return _mock_draft(request, fallback_used=True)
    if config.allow_fallback:
        return _mock_draft(request, fallback_used=False)
    raise LlmError(
        "LLM provider is required for report drafts. Set AI_PROVIDER=openai_compatible and provide AI_OPENAI_API_KEY, or explicitly set AI_ALLOW_FALLBACK=true for demos.",
        kind="provider_not_enabled",
        status_code=503,
    )


def _draft_with_llm(request: ReportDraftRequest, config) -> ReportDraftResponse:
    knowledge_sources = _knowledge_sources(request)
    result = chat_json(
        config,
        system_prompt=REPORT_DRAFT_SYSTEM_PROMPT,
        user_payload={
            **request.model_dump(by_alias=True),
            "analysisBasis": {
                "currentFindings": request.findings,
                "currentConclusion": request.conclusion,
                "reportContext": request.context,
                "instruction": "只允许使用 analysisBasis 和 providedKnowledgeSources 中的信息生成草稿。",
            },
            "providedKnowledgeSources": [
                source.model_dump(by_alias=True) for source in knowledge_sources
            ],
        },
    )
    payload = _validated_llm_payload(extract_json_object(result.content))
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
    findings = _clean(request.findings)
    conclusion = _clean(request.conclusion)

    if not findings:
        findings = "未接入真实大模型，且当前请求未提供可核验的检查所见；请医技医生结合原始影像、检验明细或病理材料补充。"
    if not conclusion:
        conclusion = "未接入真实大模型，暂不形成诊断性结论；请医技医生复核原始数据后填写。"

    if fallback_used:
        advice = "真实大模型调用失败，已降级为保守草稿：系统仅保留已提供的报告内容，不新增检查结果或诊断。请检查 AI 服务配置和日志后重试。"
    else:
        advice = "当前未配置真实大模型接口，系统仅保留已提供的报告内容，不新增检查结果或诊断。请配置 AI_PROVIDER、AI_OPENAI_BASE_URL、AI_OPENAI_API_KEY 和 AI_OPENAI_MODEL 后重新生成。"

    if request.report_type.upper() == "LAB":
        abnormal_lines = _abnormal_lab_lines(request.context)
        if abnormal_lines:
            items = "；".join(abnormal_lines[:6])
            advice = f"{advice} 已提供的异常检验指标包括：{items}。请结合临床表现复核这些指标，必要时建议复查或补充相关检查。"
        elif _has_report_content(request):
            advice = f"{advice} 当前已提供的检验内容未提示明确异常指标；如症状持续或临床不符，请结合病情复核。"

    return ReportDraftResponse(
        aiRecordId=f"ai-report-{uuid4()}",
        findings=findings,
        conclusion=conclusion,
        advice=advice,
        knowledgeSources=knowledge_sources,
        fallbackUsed=fallback_used,
    )


def _validated_llm_payload(payload: dict) -> dict[str, str]:
    expected = {}
    for field in ("findings", "conclusion", "advice"):
        value = payload[field]
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"LLM response field {field} must be a non-empty string")
        expected[field] = value.strip()
    return expected


def _clean(value: str | None) -> str:
    return (value or "").strip()


def _has_report_content(request: ReportDraftRequest) -> bool:
    return bool(_clean(request.findings) or _clean(request.conclusion) or _clean(request.context))


def _abnormal_lab_lines(context: str) -> list[str]:
    abnormal_lines = []
    for line in context.splitlines():
        text = line.strip()
        if not text:
            continue
        if ("提示=" in text and "提示=正常" not in text) or ("提示：" in text and "提示：正常" not in text):
            abnormal_lines.append(text)
    return abnormal_lines


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
