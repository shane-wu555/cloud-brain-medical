from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ScheduleSuggestion, ScheduleSuggestionRequest, ScheduleSuggestionResponse

VALID_PERIODS = {"\u4e0a\u5348", "\u4e0b\u5348", "\u5168\u5929"}
ALL_DAY = "\u5168\u5929"


def suggest(request: ScheduleSuggestionRequest) -> ScheduleSuggestionResponse:
    request = _sanitize_request(request)
    config = settings()
    if not request.candidates or not request.demands:
        return _mock_suggest(request)
    if config.llm_enabled:
        try:
            return _suggest_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_suggest(request, fallback_used=config.llm_enabled)


def _sanitize_request(request: ScheduleSuggestionRequest) -> ScheduleSuggestionRequest:
    return ScheduleSuggestionRequest(
        candidates=[
            candidate
            for candidate in request.candidates
            if candidate.doctor_id and candidate.doctor_name and candidate.department_id
        ],
        demands=[
            demand
            for demand in request.demands
            if demand.department_id and demand.work_date and demand.period in VALID_PERIODS
        ],
    )


def _suggest_with_llm(request: ScheduleSuggestionRequest, config) -> ScheduleSuggestionResponse:
    sources = _knowledge_sources(request)
    result = chat_json(
        config,
        system_prompt="""
你是医院 AI 排班建议模块。只能生成待管理员确认的排班建议，不能发布排班。
请严格输出 JSON 对象，字段为 suggestions。
suggestions 每项包含 suggestionId、doctorId、doctorName、departmentId、workDate、period、capacity、reason、requiresAdminConfirmation。
必须参考医生可用性、请假日期、手术安排、周末人流高峰、科室需求、历史挂号量、医生之间的分配均衡和 providedKnowledgeSources。
不得安排 leaveDates 或 surgeryDates 包含 workDate 的医生。
如果 candidates 中提供 unavailableSlots，不得安排 date=workDate 且 period 与需求 period 相同的医生；period=全天 与上午/下午互斥。
同一医生同一天只能安排上午、下午、全天中的一种，不能同时安排多个时段。
周末 expectedVisits 较高时可适当提高 capacity 或优先安排可用容量更高的医生，但仍需保持医生之间负载均衡。
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
    assigned_counts: dict[str, int] = {}
    assigned_doctor_dates: set[tuple[str, str]] = set()
    candidates = [
        candidate
        for candidate in request.candidates
        if candidate.doctor_id and candidate.doctor_name and candidate.department_id
    ]
    demands = [
        demand
        for demand in request.demands
        if demand.department_id and demand.work_date and demand.period in VALID_PERIODS
    ]
    for demand in demands:
        available = [
            candidate
            for candidate in candidates
            if (
                candidate.department_id == demand.department_id
                and demand.work_date not in candidate.leave_dates
                and demand.work_date not in candidate.surgery_dates
                and (candidate.doctor_id, demand.work_date) not in assigned_doctor_dates
                and not _has_unavailable_slot(candidate, demand.work_date, demand.period)
            )
        ]
        if not available:
            continue
        selected = sorted(
            available,
            key=lambda item: (
                assigned_counts.get(item.doctor_id, 0),
                -item.weekly_capacity,
                item.doctor_name,
            ),
        )[0]
        assigned_counts[selected.doctor_id] = assigned_counts.get(selected.doctor_id, 0) + 1
        assigned_doctor_dates.add((selected.doctor_id, demand.work_date))
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


def _has_unavailable_slot(candidate, work_date: str, period: str) -> bool:
    for slot in candidate.unavailable_slots:
        slot_date = str(slot.get("date", ""))
        slot_period = _normalize_period(slot.get("period", ""))
        if slot_date != work_date:
            continue
        if slot_period == period or slot_period == "全天" or period == "全天":
            return True
    return False


def _normalize_period(value) -> str:
    text = str(value or "").strip()
    upper_text = text.upper()
    if upper_text == "MORNING":
        return "\u4e0a\u5348"
    if upper_text == "AFTERNOON":
        return "\u4e0b\u5348"
    if upper_text in {"ALL_DAY", "FULL_DAY", "DAY"}:
        return ALL_DAY
    return text


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
