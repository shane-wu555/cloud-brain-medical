from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ScheduleSuggestion, ScheduleSuggestionRequest, ScheduleSuggestionResponse


def suggest(request: ScheduleSuggestionRequest) -> ScheduleSuggestionResponse:
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


def _suggest_with_llm(request: ScheduleSuggestionRequest, config) -> ScheduleSuggestionResponse:
    sources = _knowledge_sources(request)
    result = chat_json(
        config,
        system_prompt="""
你是医院 AI 排班建议模块。只能生成待管理员确认的排班建议，不能发布排班。
请严格输出 JSON 对象，字段为 suggestions。
suggestions 每项包含 suggestionId、doctorId、doctorName、departmentId、workDate、period、capacity、reason、requiresAdminConfirmation。
必须参考医生可用性、请假日期、手术安排、科室需求、历史挂号量和 providedKnowledgeSources。
不得安排 leaveDates 或 surgeryDates 包含 workDate 的医生。
capacity 必须在 1 到 100 之间。
""",
        user_payload={
            **request.model_dump(by_alias=True),
            "providedKnowledgeSources": [source.model_dump(by_alias=True) for source in sources],
        },
    )
    payload = extract_json_object(result.content)
    suggestions = [
        ScheduleSuggestion(
            suggestionId=item.get("suggestionId") or f"ai-schedule-{uuid4()}",
            doctorId=item["doctorId"],
            doctorName=item["doctorName"],
            departmentId=item["departmentId"],
            workDate=item["workDate"],
            period=item["period"],
            capacity=max(1, min(100, int(item["capacity"]))),
            reason=item.get("reason", "由 AI 根据科室需求和医生可用性生成，需管理员确认。"),
            requiresAdminConfirmation=True,
        )
        for item in payload.get("suggestions", [])
        if isinstance(item, dict)
    ]
    if not suggestions:
        raise ValueError("LLM response contains no schedule suggestions")
    return ScheduleSuggestionResponse(
        aiRecordId=f"ai-schedule-record-{uuid4()}",
        suggestions=suggestions,
        knowledgeSources=sources,
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _mock_suggest(request: ScheduleSuggestionRequest, fallback_used: bool = False) -> ScheduleSuggestionResponse:
    sources = _knowledge_sources(request)
    suggestions: list[ScheduleSuggestion] = []
    for demand in request.demands:
        available = [
            candidate
            for candidate in request.candidates
            if (
                candidate.department_id == demand.department_id
                and demand.work_date not in candidate.leave_dates
                and demand.work_date not in candidate.surgery_dates
            )
        ]
        if not available:
            continue
        selected = sorted(available, key=lambda item: (-item.weekly_capacity, item.doctor_name))[0]
        baseline = demand.historical_visits if demand.historical_visits is not None else demand.expected_visits
        capacity = max(8, min(60, int(max(demand.expected_visits, baseline) * 1.15)))
        source_label = sources[0].title if sources else "本院排班规则"
        suggestions.append(
            ScheduleSuggestion(
                suggestionId=f"ai-schedule-{uuid4()}",
                doctorId=selected.doctor_id,
                doctorName=selected.doctor_name,
                departmentId=selected.department_id,
                workDate=demand.work_date,
                period=demand.period,
                capacity=capacity,
                reason=f"结合科室需求 {demand.expected_visits} 人次、历史量 {baseline}、风险等级 {demand.risk_level} 和医生可用容量生成；参考来源：{source_label}。需管理员确认后发布。",
            )
        )
    return ScheduleSuggestionResponse(
        aiRecordId=f"ai-schedule-record-{uuid4()}",
        suggestions=suggestions,
        knowledgeSources=sources,
        fallbackUsed=fallback_used,
    )


def _knowledge_sources(request: ScheduleSuggestionRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        [
            "排班 科室需求 历史挂号 医生 请假 号源 管理员确认",
            *[candidate.specialty for candidate in request.candidates if candidate.specialty],
            *[demand.department_id for demand in request.demands],
        ]
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
        for source in retrieve(query, limit=5)
    ]
