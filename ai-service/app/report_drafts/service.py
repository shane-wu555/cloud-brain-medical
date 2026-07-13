import re
from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ReportDraftRequest, ReportDraftResponse


REPORT_DRAFT_SYSTEM_PROMPT = """
你是医院医技诊断专家，负责直接撰写可放入检查/检验报告的“检查所见、结论、后续建议”。
你必须严格输出 JSON 对象，字段只能包含 findings、conclusion、advice。

写作规则：
1. 以检查医生、检验医生或病理医生的专业口吻书写，不要自称系统、模型、AI、助手或辅助工具。
2. findings 写正式报告所见，只描述已提供的影像征象、检验指标或病理材料；不要写“模型分类、置信度、分割、检测提示、AI分析提示”等技术过程。
3. conclusion 写一行诊断性结论，直接给出医学判断；不要出现“AI、辅助、提示、建议复核、需确认、结合原始图像、人工复核、仅供参考、模型”等字样。
4. advice 写给临床的后续处理建议，可以多行；不要出现“请医生确认、请人工复核、需结合原始数据、AI提示”等来源或免责话术。
5. 只能依据 analysisBasis 和 providedKnowledgeSources 中已有信息生成内容，不得编造未提供的症状、病史、检查结果、影像征象、检验数值、病理诊断或治疗方案。
6. 信息不足时，用中性正式报告语言表达为“资料有限，建议结合临床进一步评估”，不要写“信息不足，需要医生复核/确认”。
7. reportType=LAB 时，findings 和 conclusion 必须以真实检验明细、当前报告和病理诊断为准；advice 只围绕已提供的异常指标、参考范围和报告结论。
8. reportType=CHECK 时，如果没有影像分析结果或医生填写的所见，不得声称“未见异常”或“检查完成”。
9. 输出内容必须可直接粘贴进正式报告，不要包含标题前缀，例如“检查所见建议：”“结论建议：”“后续建议：”。
"""

_BANNED_REPORT_TERMS = (
    "AI",
    "人工智能",
    "辅助",
    "模型",
    "置信度",
    "分类",
    "分割",
    "检测提示",
    "分析提示",
    "提示",
    "请复核",
    "复核",
    "需确认",
    "确认",
    "人工",
    "仅供参考",
    "结合原始图像",
    "结合原始薄层图像",
    "原始数据",
)


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
                "instruction": "只允许使用 analysisBasis 和 providedKnowledgeSources 中的信息生成正式报告内容；不要出现 AI、辅助、模型、置信度、复核、确认等过程性话术。",
            },
            "providedKnowledgeSources": [
                source.model_dump(by_alias=True) for source in knowledge_sources
            ],
        },
    )
    payload = _validated_llm_payload(extract_json_object(result.content))
    payload = _sanitize_payload(payload, request)
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
        findings = "资料有限，当前未提供可形成完整报告所见的检查、检验或病理资料。"
    if not conclusion:
        conclusion = "资料有限，暂不形成明确诊断性结论。"

    advice = "建议结合临床表现及既往资料进一步评估，必要时完善相关检查。"
    if request.report_type.upper() == "LAB":
        abnormal_lines = _abnormal_lab_lines(request.context)
        if abnormal_lines:
            items = "；".join(abnormal_lines[:6])
            advice = f"异常检验指标包括：{items}。建议结合临床表现评估，必要时复查或补充相关检查。"
        elif _has_report_content(request):
            advice = "当前检验内容未提示明确异常指标；如症状持续或临床表现不符，建议结合病情进一步评估。"

    payload = _sanitize_payload(
        {"findings": findings, "conclusion": conclusion, "advice": advice},
        request,
    )
    return ReportDraftResponse(
        aiRecordId=f"ai-report-{uuid4()}",
        findings=payload["findings"],
        conclusion=payload["conclusion"],
        advice=payload["advice"],
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


def _sanitize_payload(payload: dict[str, str], request: ReportDraftRequest) -> dict[str, str]:
    sanitized = {field: _sanitize_report_text(value) for field, value in payload.items()}
    if not sanitized["findings"]:
        sanitized["findings"] = _clean(request.findings) or "资料有限，当前未提供可形成完整报告所见的检查、检验或病理资料。"
    if not sanitized["conclusion"]:
        sanitized["conclusion"] = _clean(request.conclusion) or "资料有限，暂不形成明确诊断性结论。"
    if not sanitized["advice"]:
        sanitized["advice"] = "建议结合临床表现及既往资料进一步评估，必要时完善相关检查。"
    sanitized["conclusion"] = _one_line(sanitized["conclusion"])
    return sanitized


def _sanitize_report_text(value: str | None) -> str:
    text = _clean(value)
    for prefix in ("检查所见建议：", "检查所见：", "结论建议：", "结论：", "后续建议：", "建议："):
        text = text.replace(prefix, "")
    text = re.sub(r"影像\s*AI\s*分析提示[：:]\s*", "", text, flags=re.IGNORECASE)
    text = re.sub(r"AI\s*辅助\s*(检测|诊断)?\s*提示[：:：]?", "", text, flags=re.IGNORECASE)
    text = re.sub(r"模型\s*(分类|判断)?\s*为[“\"']?([^，。；;、）)]*)[”\"']?", r"\2", text)
    text = re.sub(r"置信度\s*[:：]?\s*[0-9.％%]+", "", text)
    text = re.sub(r"（\s*高风险\s*[，,]\s*）", "（高风险）", text)
    text = re.sub(r"[(（][^）)]*(AI|人工智能|模型|置信度|分割|检测提示|分析提示)[^）)]*[）)]", "", text, flags=re.IGNORECASE)
    text = re.sub(r"[^。；;]*?(请|需|需要|建议)?[^。；;]*?(复核|确认|人工复核|结合原始图像|结合原始薄层图像|原始数据|仅供参考)[^。；;]*?[。；;]", "", text)
    for term in _BANNED_REPORT_TERMS:
        text = text.replace(term, "")
    text = re.sub(r"\s+", " ", text)
    text = (
        text.replace("（高风险，）", "（高风险）")
        .replace("(高风险，)", "(高风险)")
        .replace("，，", "，")
        .replace("，。", "。")
        .replace("：。", "。")
        .replace("；。", "。")
    )
    cleaned = text.strip(" \n\t；;，,。")
    return cleaned + ("。" if cleaned else "")


def _one_line(value: str) -> str:
    return " ".join(part.strip() for part in value.splitlines() if part.strip())


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
