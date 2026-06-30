import logging
import time
from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ScheduleSuggestion, ScheduleSuggestionRequest, ScheduleSuggestionResponse

VALID_PERIODS = {"\u4e0a\u5348", "\u4e0b\u5348", "\u5168\u5929"}
ALL_DAY = "\u5168\u5929"
SCHEDULE_RAG_SOURCE_TYPES = ("HOSPITAL_RULE", "DEPARTMENT", "DOCTOR", "SCHEDULE", "DOCTOR_EVENT")
logger = logging.getLogger(__name__)


def suggest(request: ScheduleSuggestionRequest) -> ScheduleSuggestionResponse:
    started_at = time.perf_counter()
    request = _sanitize_request(request)
    config = settings()
    logger.info(
        "Schedule suggestion request sanitized: candidates=%s, demands=%s, llmEnabled=%s",
        len(request.candidates),
        len(request.demands),
        config.llm_enabled,
    )
    if not request.candidates or not request.demands:
        response = _mock_suggest(request)
        logger.info(
            "Schedule suggestion completed by local rules: elapsedMs=%s, suggestions=%s",
            round((time.perf_counter() - started_at) * 1000),
            len(response.suggestions),
        )
        return response
    if config.llm_enabled:
        try:
            response = _suggest_with_llm(request, config)
            logger.info(
                "Schedule suggestion completed by LLM: elapsedMs=%s, suggestions=%s, ragSources=%s, provider=%s, model=%s",
                round((time.perf_counter() - started_at) * 1000),
                len(response.suggestions),
                len(response.knowledge_sources),
                response.provider,
                response.model,
            )
            return response
        except (LlmError, ValueError, KeyError) as exc:
            logger.warning("Schedule suggestion LLM failed, fallbackAllowed=%s: %s", config.allow_fallback, exc)
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    response = _mock_suggest(request, fallback_used=config.llm_enabled)
    logger.info(
        "Schedule suggestion completed by local fallback: elapsedMs=%s, suggestions=%s, ragSources=%s",
        round((time.perf_counter() - started_at) * 1000),
        len(response.suggestions),
        len(response.knowledge_sources),
    )
    return response


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
    started_at = time.perf_counter()
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
    suggestions: list[ScheduleSuggestion] = []
    assigned_doctor_dates: set[tuple[str, str]] = set()
    raw_suggestions = payload.get("suggestions", [])
    for item in raw_suggestions:
        if not isinstance(item, dict):
            continue
        suggestion = _coerce_llm_suggestion(item, request, assigned_doctor_dates)
        if suggestion is not None:
            suggestions.append(suggestion)
    logger.info(
        "Schedule LLM response parsed: elapsedMs=%s, rawSuggestions=%s, acceptedSuggestions=%s, ragSources=%s",
        round((time.perf_counter() - started_at) * 1000),
        len(raw_suggestions) if isinstance(raw_suggestions, list) else 0,
        len(suggestions),
        len(sources),
    )
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


def _coerce_llm_suggestion(item: dict, request: ScheduleSuggestionRequest, assigned_doctor_dates: set[tuple[str, str]]) -> ScheduleSuggestion | None:
    doctor_id = str(item.get("doctorId") or "")
    department_id = str(item.get("departmentId") or "")
    work_date = str(item.get("workDate") or "")
    period = _normalize_period(item.get("period"))
    candidate = next((candidate for candidate in request.candidates if candidate.doctor_id == doctor_id), None)
    if candidate is None or candidate.department_id != department_id:
        return None
    demand = next(
        (
            demand
            for demand in request.demands
            if demand.department_id == department_id and demand.work_date == work_date and demand.period == period
        ),
        None,
    )
    if demand is None:
        return None
    if (doctor_id, work_date) in assigned_doctor_dates:
        return None
    if _is_unavailable(candidate, work_date, period):
        return None
    assigned_doctor_dates.add((doctor_id, work_date))
    return ScheduleSuggestion(
        suggestionId=item.get("suggestionId") or f"ai-schedule-{uuid4()}",
        doctorId=candidate.doctor_id,
        doctorName=candidate.doctor_name,
        departmentId=candidate.department_id,
        workDate=work_date,
        period=period,
        capacity=_capacity(item.get("capacity")),
        reason=str(item.get("reason") or "由 AI 根据本地科室需求、医生可用性和院内知识来源生成，需管理员确认。"),
        requiresAdminConfirmation=True,
    )


def _capacity(value) -> int:
    try:
        return max(1, min(100, int(value)))
    except (TypeError, ValueError):
        return 20


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
                and (candidate.doctor_id, demand.work_date) not in assigned_doctor_dates
                and not _is_unavailable(candidate, demand.work_date, demand.period)
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


def _is_unavailable(candidate, work_date: str, period: str) -> bool:
    if candidate.unavailable_slots:
        return _has_unavailable_slot(candidate, work_date, period)
    return work_date in candidate.leave_dates or work_date in candidate.surgery_dates


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
            "AI智能排班 门诊排班 科室需求 历史挂号 医生 请假 手术 号源 管理员确认",
            *[candidate.doctor_name for candidate in request.candidates if candidate.doctor_name],
            *[candidate.specialty for candidate in request.candidates if candidate.specialty],
            *[demand.department_id for demand in request.demands],
            *[demand.work_date for demand in request.demands],
            *[demand.period for demand in request.demands],
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
        for source in retrieve(query, limit=8, source_types=SCHEDULE_RAG_SOURCE_TYPES)
    ]
