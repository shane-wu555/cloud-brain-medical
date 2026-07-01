import logging
import time
from uuid import uuid4

from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output

from .models import ScheduleSuggestion, ScheduleSuggestionRequest, ScheduleSuggestionResponse

VALID_PERIODS = {"\u4e0a\u5348", "\u4e0b\u5348", "\u5168\u5929"}
ALL_DAY = "\u5168\u5929"
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
                "Schedule suggestion completed by LLM: elapsedMs=%s, suggestions=%s, knowledgeSources=%s, provider=%s, model=%s",
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
        "Schedule suggestion completed by local fallback: elapsedMs=%s, suggestions=%s, knowledgeSources=%s",
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
        backgroundSummary=request.background_summary,
    )


def _suggest_with_llm(request: ScheduleSuggestionRequest, config) -> ScheduleSuggestionResponse:
    started_at = time.perf_counter()
    result = chat_json(
        config,
        system_prompt="""
你是医院 AI 排班建议模块。后端已经筛选出需要重排的科室、诊室、日期和时段，你只需要为输入 demands 生成待管理员确认的医生排班建议，不要判断哪些科室需要重排。
请严格输出 JSON 对象，字段为 suggestions。
suggestions 每项包含 suggestionId、doctorId、doctorName、departmentId、roomId、roomName、workDate、period、capacity、requiresAdminConfirmation，不要输出排班理由。
只参考 candidates 中的医生基础信息、unavailableSlots，以及 demands 中的 departmentId、roomId、workDate、period、expectedVisits；只有请求提供 backgroundSummary 时才参考历史流量摘要。
unavailableSlots 由后端从 doctor_event 表按本次重排日期窗口展开，格式为 {date, period, type}。如果某医生存在 date=workDate 且 period 与需求 period 冲突的 unavailableSlot，就不能安排该医生；period=全天 与上午/下午互斥。
同一医生同一日期同一时段只能安排一次；period=全天 与上午/下午互斥。
同一 roomId 在同一日期同一时段必须有且仅有一个医生；如果 demand 提供 roomId，建议医生必须属于该 roomId。
同一医生同一天尽量上午/下午状态一致：能排则优先排满当天，不能排则优先整天不排，除非可用性或诊室需求不允许。
capacity 必须在 1 到 70 之间。
尽量考虑同诊室不同医生之间的负载均衡。
""",
        user_payload={
            **request.model_dump(by_alias=True),
        },
    )
    payload = extract_json_object(result.content)
    suggestions: list[ScheduleSuggestion] = []
    assigned_doctor_slots: set[tuple[str, str, str]] = set()
    assigned_room_slots: set[tuple[str, str, str]] = set()
    raw_suggestions = payload.get("suggestions", [])
    for item in raw_suggestions:
        if not isinstance(item, dict):
            continue
        suggestion = _coerce_llm_suggestion(item, request, assigned_doctor_slots, assigned_room_slots)
        if suggestion is not None:
            suggestions.append(suggestion)
    logger.info(
        "Schedule LLM response parsed: elapsedMs=%s, rawSuggestions=%s, acceptedSuggestions=%s, knowledgeSources=%s",
        round((time.perf_counter() - started_at) * 1000),
        len(raw_suggestions) if isinstance(raw_suggestions, list) else 0,
        len(suggestions),
        0,
    )
    if not suggestions:
        raise ValueError("LLM response contains no schedule suggestions")
    return ScheduleSuggestionResponse(
        aiRecordId=f"ai-schedule-record-{uuid4()}",
        suggestions=suggestions,
        knowledgeSources=[],
        backgroundSummary=_background_summary(request),
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _coerce_llm_suggestion(
    item: dict,
    request: ScheduleSuggestionRequest,
    assigned_doctor_slots: set[tuple[str, str, str]],
    assigned_room_slots: set[tuple[str, str, str]],
) -> ScheduleSuggestion | None:
    doctor_id = str(item.get("doctorId") or "")
    department_id = str(item.get("departmentId") or "")
    work_date = str(item.get("workDate") or "")
    period = _normalize_period(item.get("period"))
    candidate = next((candidate for candidate in request.candidates if candidate.doctor_id == doctor_id), None)
    if candidate is None or candidate.department_id != department_id:
        return None
    room_id = str(item.get("roomId") or candidate.room_id or "")
    if candidate.room_id and room_id and candidate.room_id != room_id:
        return None
    demand = next(
        (
            demand
            for demand in request.demands
            if (
                demand.department_id == department_id
                and demand.work_date == work_date
                and demand.period == period
                and (not demand.room_id or demand.room_id == room_id or demand.room_id == candidate.room_id)
            )
        ),
        None,
    )
    if demand is None:
        return None
    room_id = room_id or demand.room_id
    room_name = str(item.get("roomName") or candidate.room_name or demand.room_name or "")
    if _slot_conflicts(assigned_doctor_slots, doctor_id, work_date, period):
        return None
    if room_id and _slot_conflicts(assigned_room_slots, room_id, work_date, period):
        return None
    if _is_unavailable(candidate, work_date, period):
        return None
    _reserve_slot(assigned_doctor_slots, doctor_id, work_date, period)
    if room_id:
        _reserve_slot(assigned_room_slots, room_id, work_date, period)
    return ScheduleSuggestion(
        suggestionId=item.get("suggestionId") or f"ai-schedule-{uuid4()}",
        doctorId=candidate.doctor_id,
        doctorName=candidate.doctor_name,
        departmentId=candidate.department_id,
        roomId=room_id,
        roomName=room_name,
        workDate=work_date,
        period=period,
        capacity=_capacity(item.get("capacity")),
        requiresAdminConfirmation=True,
    )


def _capacity(value) -> int:
    try:
        return max(1, min(100, int(value)))
    except (TypeError, ValueError):
        return 20


def _mock_suggest(request: ScheduleSuggestionRequest, fallback_used: bool = False) -> ScheduleSuggestionResponse:
    suggestions: list[ScheduleSuggestion] = []
    assigned_counts: dict[str, int] = {}
    assigned_doctor_slots: set[tuple[str, str, str]] = set()
    assigned_room_slots: set[tuple[str, str, str]] = set()
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
    for demand in sorted(demands, key=lambda item: (-item.expected_visits, item.work_date, _period_order(item.period), item.room_id)):
        available = [
            candidate
            for candidate in candidates
            if (
                candidate.department_id == demand.department_id
                and (not demand.room_id or candidate.room_id == demand.room_id)
                and not _slot_conflicts(assigned_doctor_slots, candidate.doctor_id, demand.work_date, demand.period)
                and (not candidate.room_id or not _slot_conflicts(assigned_room_slots, candidate.room_id, demand.work_date, demand.period))
                and not _is_unavailable(candidate, demand.work_date, demand.period)
            )
        ]
        if not available:
            continue
        weekday = _is_weekday(demand.work_date)
        selected = sorted(
            available,
            key=lambda item: (
                assigned_counts.get(item.doctor_id, 0),
                -_doctor_demand_score(item) if weekday else _doctor_demand_score(item),
                -item.weekly_capacity,
                item.doctor_name,
            ),
        )[0]
        assigned_counts[selected.doctor_id] = assigned_counts.get(selected.doctor_id, 0) + 1
        _reserve_slot(assigned_doctor_slots, selected.doctor_id, demand.work_date, demand.period)
        if selected.room_id:
            _reserve_slot(assigned_room_slots, selected.room_id, demand.work_date, demand.period)
        baseline = demand.historical_visits if demand.historical_visits is not None else demand.expected_visits
        capacity = max(8, min(60, int(max(demand.expected_visits, baseline) * 1.15)))
        suggestions.append(
            ScheduleSuggestion(
                suggestionId=f"ai-schedule-{uuid4()}",
                doctorId=selected.doctor_id,
                doctorName=selected.doctor_name,
                departmentId=selected.department_id,
                roomId=selected.room_id or demand.room_id,
                roomName=selected.room_name or demand.room_name,
                workDate=demand.work_date,
                period=demand.period,
                capacity=capacity,
            )
        )
    return ScheduleSuggestionResponse(
        aiRecordId=f"ai-schedule-record-{uuid4()}",
        suggestions=suggestions,
        knowledgeSources=[],
        backgroundSummary=_background_summary(request),
        fallbackUsed=fallback_used,
    )


def _is_unavailable(candidate, work_date: str, period: str) -> bool:
    return _has_unavailable_slot(candidate, work_date, period)


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


def _period_order(period: str) -> int:
    return {"上午": 0, "下午": 1, ALL_DAY: 2}.get(period, 9)


def _is_weekday(work_date: str) -> bool:
    try:
        from datetime import date

        return date.fromisoformat(work_date).weekday() < 5
    except ValueError:
        return True


def _doctor_demand_score(candidate) -> int:
    return max(candidate.historical_average_visits, candidate.weekly_capacity)


def _slot_conflicts(assigned_slots: set[tuple[str, str, str]], owner_id: str, work_date: str, period: str) -> bool:
    if not owner_id:
        return False
    if period == ALL_DAY:
        return any((owner_id, work_date, existing) in assigned_slots for existing in ("上午", "下午", ALL_DAY))
    return (owner_id, work_date, period) in assigned_slots or (owner_id, work_date, ALL_DAY) in assigned_slots


def _reserve_slot(assigned_slots: set[tuple[str, str, str]], owner_id: str, work_date: str, period: str) -> None:
    if owner_id:
        assigned_slots.add((owner_id, work_date, period))


def _background_summary(request: ScheduleSuggestionRequest) -> str:
    return request.background_summary
