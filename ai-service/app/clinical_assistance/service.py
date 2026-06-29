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

    # 构建可用项目清单文本
    exam_catalog = "\n".join(
        f'  - name="{i.get("name", "")}" code="{i.get("code", "")}" category="{i.get("category", "")}"'
        for i in request.available_exam_items
    ) or "  （未提供目录）"

    drug_catalog = "\n".join(
        f'  - drugName="{i.get("drugName", "")}" spec="{i.get("specification", "")}"'
        for i in request.available_drugs
    ) or "  （未提供目录）"

    system_prompt = f"""你是医院 HIS 系统的医生辅助模块。严格返回 JSON，字段为 suggestions 数组。

【必须输出的4种类型，每种恰好1条】
1. kind="diagnosis" label="鉴别诊断"
   content: 编号列出鉴别诊断，每病名+简短鉴别要点，≤30字/条
   metadata: {{"primaryDiagnosis": "最可能的简短诊断名称"}}
   ★ 默认只列2种；仅在症状高度不典型、多病共存或鉴别困难时才增加，最多不超过4种
   ★ 第1条必须是最可能的诊断，按可能性由高到低排列

2. kind="exam" label="检查建议"
   content: 编号列出检查项目名称+适应证
   metadata: {{"projectNames": [...]}}
   ★ projectNames 必须从下方【本院检查目录】中选择，使用 name 字段的精确值
   ★ 默认只推荐1项最必要的检查；仅在临床上确有必要同时完成多项时才增加，最多不超过2项

3. kind="medication" label="用药建议"
   content: 编号列出 药名+剂量+用法+疗程
   metadata: {{"drugs": [{{"drugName":"...","dosage":"...","usage":"口服","frequency":"...","days":整数}}]}}
   ★ drugName 必须从下方【本院药品目录】中选择，使用 drugName 字段的精确值

4. kind="advice" label="临床建议"
   content: 编号列出临床指导，每条≤20字
   metadata: null

【本院检查目录】（只能从此列表选）：
{exam_catalog}

【本院药品目录】（只能从此列表选）：
{drug_catalog}

【规则】不编造目录外的项目和药品；label不含"AI""草稿"；只输出JSON。"""

    result = chat_json(
        config,
        system_prompt=system_prompt,
        user_payload={
            "patientContext": {
                "chiefComplaint": request.chief_complaint,
                "presentIllness": request.present_illness,
                "pastHistory": request.past_history,
                "allergyHistory": request.allergy_history,
            },
            "doctorPrompt": request.prompt,
        },
    )
    payload = extract_json_object(result.content)
    suggestions = [
        ClinicalSuggestion(
            kind=item["kind"],
            label=item["label"],
            content=item["content"],
            source=item.get("source", "LLM"),
            metadata=item.get("metadata"),
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
        for value in [
            request.chief_complaint,
            request.present_illness,
            request.past_history,
            request.allergy_history,
            request.prompt,
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
        for source in retrieve(query)
    ]
