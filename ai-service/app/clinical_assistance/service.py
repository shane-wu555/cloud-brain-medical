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
    assistance_type = (request.assistance_type or "initial").lower()

    # 构建可用项目清单文本
    exam_catalog = "\n".join(
        f'  - name="{i.get("name", "")}" code="{i.get("code", "")}" category="{i.get("category", "")}"'
        for i in request.available_exam_items
    ) or "  （未提供目录）"

    drug_catalog = "\n".join(
        f'  - drugName="{i.get("drugName", "")}" spec="{i.get("specification", "")}"'
        for i in request.available_drugs
    ) or "  （未提供目录）"

    historical_records = "\n".join(
        f'  - visitDate="{i.get("visitDate", "")}" chiefComplaint="{i.get("chiefComplaint", "")}" diagnosis="{i.get("diagnosis", "") or i.get("preliminaryDiagnosis", "")}" treatmentPlan="{i.get("treatmentPlan", "")}"'
        for i in request.historical_records[:5]
    ) or "  （未提供历史病历）"

    report_results = "\n".join(
        f'  - reportType="{i.get("reportType", "")}" itemName="{i.get("itemName", "")}" conclusion="{i.get("conclusion", "")}" findings="{i.get("findings", "")}"'
        for i in request.report_results[:8]
    ) or "  （未提供正式报告）"

    if assistance_type == "post_report":
        system_prompt = f"""你是医院 HIS 系统的后续诊疗与处方建议助手。严格返回 JSON，字段为 suggestions 数组。

【必须输出的3种类型，每种恰好1条】
1. kind="diagnosis" label="确诊参考"
   content: 基于当前诊断、病历和已发布报告给出诊断参考；如未提供报告，则明确依据当前诊断和病历，必须说明仍需医生确认
   metadata: {{"primaryDiagnosis": "最可能的简短诊断名称"}}

2. kind="medication" label="处方建议"
   content: 编号列出 药名+本院规格+剂量+用法+疗程
   metadata: {{"drugs": [{{"drugName":"...","specification":"...","dosage":"...","usage":"口服","frequency":"...","days":整数}}]}}
   ★ drugName 和 specification 必须从下方【本院药品目录】中选择，使用精确值
   ★ 如果当前诊断、病历和报告不足以支持用药，metadata.drugs 返回空数组，并说明需医生人工开方

3. kind="advice" label="后续建议"
   content: 编号列出复诊、观察、生活方式或进一步处理建议，每条≤24字
   metadata: null

【正式检查/检验报告】（如未提供，可依据当前诊断和病历判断）：
{report_results}

【本院药品目录】（只能从此列表选）：
{drug_catalog}

【规则】不得编造目录外药品；不得把建议当作生效处方；报告缺失时不得假设检查结果；label不含"AI""草稿"；只输出JSON。"""
    else:
        system_prompt = f"""你是医院 HIS 系统的初诊辅助模块。严格返回 JSON，字段为 suggestions 数组。

【必须输出的2种类型，每种恰好1条】
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

【本院检查目录】（只能从此列表选）：
{exam_catalog}

【历史病历摘要】（可参考但不得替代本次判断）：
{historical_records}

【规则】不编造目录外检查项目；初诊阶段不输出用药建议；label不含"AI""草稿"；只输出JSON。"""

    result = chat_json(
        config,
        system_prompt=system_prompt,
        user_payload={
            "assistanceType": assistance_type,
            "patientContext": {
                "chiefComplaint": request.chief_complaint,
                "presentIllness": request.present_illness,
                "pastHistory": request.past_history,
                "allergyHistory": request.allergy_history,
                "diagnosis": request.diagnosis,
            },
            "historicalRecords": request.historical_records[:5],
            "reportResults": request.report_results[:8],
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
    if (request.assistance_type or "initial").lower() == "post_report":
        report_text = "；".join(str(item.get("conclusion", "")) for item in request.report_results if item.get("conclusion")) or "暂无正式报告结论"
        diagnosis = request.diagnosis.strip() or complaint
        return ClinicalAssistanceResponse(
            aiRecordId=f"ai-assist-{uuid4()}",
            suggestions=[
                ClinicalSuggestion(
                    kind="diagnosis",
                    label="确诊参考",
                    content=f"结合当前诊断“{diagnosis}”、主诉“{complaint}”及报告结论“{report_text}”，请医生综合查体后确认。",
                    source=knowledge_sources[0].source_id,
                    metadata={"primaryDiagnosis": diagnosis},
                ),
                ClinicalSuggestion(
                    kind="advice",
                    label="后续建议",
                    content="1. 结合正式报告复核诊断\n2. 关注症状变化并安排复诊",
                    source=knowledge_sources[-1].source_id,
                ),
            ],
            knowledgeSources=knowledge_sources,
            fallbackUsed=fallback_used,
        )
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
            " ".join(str(item.get("diagnosis", "")) for item in request.historical_records),
            " ".join(str(item.get("conclusion", "")) for item in request.report_results),
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
