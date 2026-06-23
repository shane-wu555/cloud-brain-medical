from uuid import uuid4

from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import (
    ClinicalAssistanceRequest,
    ClinicalAssistanceResponse,
    ClinicalKnowledgeSource,
    ClinicalSuggestion,
)


def assist(request: ClinicalAssistanceRequest) -> ClinicalAssistanceResponse:
    config = settings()
    if config.llm_enabled:
        try:
            return _assist_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_assist(request, fallback_used=config.llm_enabled)


def _assist_with_llm(request: ClinicalAssistanceRequest, config) -> ClinicalAssistanceResponse:
    knowledge_sources = _knowledge_sources(request)
    system_prompt = """
你是医院 HIS 系统中的医生辅助模块，只能生成辅助建议，不能给出最终诊断或生效处方。
请严格输出 JSON 对象，字段为 suggestions。
suggestions 是数组，每项包含 kind、label、content、source。
kind 只能使用 diagnosis、exam、medication、risk、advice。
必须优先参考 providedKnowledgeSources，并提醒医生结合查体、正式报告和临床判断人工确认。
若出现意识障碍、突发剧烈头痛、抽搐、偏瘫等危险信号，必须输出 risk 建议并提示急诊优先。
不要编造患者未提供的信息，不要声称已确诊。
"""
    result = chat_json(
        config,
        system_prompt=system_prompt,
        user_payload={
            "appointmentId": request.appointment_id,
            "patientId": request.patient_id,
            "chiefComplaint": request.chief_complaint,
            "presentIllness": request.present_illness,
            "doctorPrompt": request.prompt,
            "providedKnowledgeSources": [
                source.model_dump(by_alias=True) for source in knowledge_sources
            ],
            "outputContract": {
                "suggestions": [
                    {
                        "kind": "diagnosis",
                        "label": "AI 鉴别诊断草稿",
                        "content": "建议内容，必须说明需医生确认",
                        "source": "LLM",
                    }
                ]
            },
        },
    )
    payload = extract_json_object(result.content)
    suggestions = [
        ClinicalSuggestion(
            kind=item["kind"],
            label=item["label"],
            content=item["content"],
            source=item.get("source", "LLM"),
        )
        for item in payload.get("suggestions", [])
        if isinstance(item, dict)
    ]
    if not suggestions:
        raise ValueError("LLM response contains no suggestions")
    return ClinicalAssistanceResponse(
        aiRecordId=f"ai-assist-{uuid4()}",
        suggestions=suggestions[:6],
        knowledgeSources=knowledge_sources,
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _mock_assist(request: ClinicalAssistanceRequest, fallback_used: bool = False) -> ClinicalAssistanceResponse:
    knowledge_sources = _knowledge_sources(request)
    complaint = request.chief_complaint.strip() or "当前症状"
    history = request.present_illness.strip()
    context = f"；现病史：{history}" if history else ""
    return ClinicalAssistanceResponse(
        aiRecordId=f"ai-assist-{uuid4()}",
        suggestions=[
            ClinicalSuggestion(
                kind="diagnosis",
                label="AI 鉴别诊断草稿",
                content=f"基于主诉“{complaint}”{context}，建议结合神经系统查体、生命体征及正式检查报告形成鉴别诊断。参考：{knowledge_sources[0].title}",
                source=knowledge_sources[0].source_id,
            ),
            ClinicalSuggestion(
                kind="advice",
                label="进一步检查建议",
                content=(request.prompt.strip() or "结合本次病历评估进一步检查")
                + "；若存在意识障碍、突发剧烈头痛或局灶神经体征，应优先按急诊规则处置。",
                source=knowledge_sources[-1].source_id,
            ),
        ],
        knowledgeSources=knowledge_sources,
        fallbackUsed=fallback_used,
    )


def _knowledge_sources(request: ClinicalAssistanceRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        value
        for value in [request.chief_complaint, request.present_illness, request.prompt]
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
        for source in retrieve(query)
    ]
