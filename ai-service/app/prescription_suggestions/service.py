from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import MedicationSuggestion, PrescriptionSuggestionRequest, PrescriptionSuggestionResponse


def suggest_prescription(request: PrescriptionSuggestionRequest) -> PrescriptionSuggestionResponse:
    config = settings()
    if config.llm_enabled:
        try:
            return _suggest_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_suggest(request, fallback_used=config.llm_enabled)


def _suggest_with_llm(request: PrescriptionSuggestionRequest, config) -> PrescriptionSuggestionResponse:
    knowledge_sources = _knowledge_sources(request)
    result = chat_json(
        config,
        system_prompt="""
你是医院处方建议助手，只能生成用药建议草稿，不能生成生效处方。
请严格输出 JSON 对象，字段为 suggestions 和 warnings。
suggestions 每项包含 drugName、dosage、usage、frequency、days、note、source。
必须优先参考 providedKnowledgeSources 中的本院药品目录和院内用药安全规则。
如果 allergyHistory 或 medicationHistory 与建议冲突，必须在 warnings 中提示。
不得编造本院药品目录中不存在的药品；无法确定时返回空 suggestions 并说明需医生人工开方。
""",
        user_payload={
            **request.model_dump(by_alias=True),
            "providedKnowledgeSources": [
                source.model_dump(by_alias=True) for source in knowledge_sources
            ],
        },
    )
    payload = extract_json_object(result.content)
    suggestions = [
        MedicationSuggestion(
            drugName=item["drugName"],
            dosage=item["dosage"],
            usage=item["usage"],
            frequency=item["frequency"],
            days=int(item.get("days") or 1),
            note=item["note"],
            source=item.get("source") or (knowledge_sources[0].source_id if knowledge_sources else "AI"),
        )
        for item in payload.get("suggestions", [])
        if isinstance(item, dict)
    ]
    return PrescriptionSuggestionResponse(
        aiRecordId=f"ai-rx-{uuid4()}",
        suggestions=suggestions[:5],
        warnings=[str(item) for item in payload.get("warnings", [])],
        knowledgeSources=knowledge_sources,
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _mock_suggest(request: PrescriptionSuggestionRequest, fallback_used: bool = False) -> PrescriptionSuggestionResponse:
    knowledge_sources = _knowledge_sources(request)
    drug_source = next((source for source in knowledge_sources if source.source_type == "DRUG"), None)
    safety_source = next((source for source in knowledge_sources if source.source_type == "HOSPITAL_RULE"), None)
    suggestions: list[MedicationSuggestion] = []
    if drug_source:
        suggestions.append(
            MedicationSuggestion(
                drugName=drug_source.title.replace("药品：", ""),
                dosage="请医生按药品说明书和患者情况确定剂量",
                usage="遵医嘱",
                frequency="遵医嘱",
                days=1,
                note="AI 建议仅作为草稿，需结合过敏史、禁忌证、肝肾功能和正式诊断人工确认。",
                source=drug_source.source_id,
            )
        )
    warnings = ["正式处方必须由门诊医生人工确认，AI 不得直接生效。"]
    if request.allergy_history:
        warnings.append("患者存在过敏史，请医生核对药品禁忌证。")
    if safety_source:
        warnings.append(f"参考安全规则：{safety_source.title}")
    return PrescriptionSuggestionResponse(
        aiRecordId=f"ai-rx-{uuid4()}",
        suggestions=suggestions,
        warnings=warnings,
        knowledgeSources=knowledge_sources,
        fallbackUsed=fallback_used,
    )


def _knowledge_sources(request: PrescriptionSuggestionRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        value
        for value in [
            request.diagnosis,
            request.chief_complaint,
            request.allergy_history,
            request.medication_history,
            request.prompt,
            "药品 用药 处方",
        ]
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
        for source in retrieve(query, limit=6)
    ]
