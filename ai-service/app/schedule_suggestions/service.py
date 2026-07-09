import logging
import time
from uuid import uuid4

from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output

from .models import ScheduleSuggestion, ScheduleSuggestionRequest, ScheduleSuggestionResponse

VALID_PERIODS = {"\u4e0a\u5348", "\u4e0b\u5348"}
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
unavailableSlots 由后端从 doctor_event 表按本次重排日期窗口展开，格式为 {date, period, type}。如果某医生存在 date=workDate 且 period 与需求 period 相同的 unavailableSlot，就不能安排该医生。
同一医生同一日期同一时段只能安排一次；排班时段只允许上午或下午。
同一诊室同一日期如果同时有上午和下午需求，优先安排同一名可用医生覆盖两个时段；只有可用性、诊室冲突或需求不允许时才拆给不同医生。
同一 roomId 在同一日期同一时段必须有且仅有一个医生；如果 demand 提供 roomId，建议医生必须属于该 roomId。
周一到周五客流较大的时段，可优先安排主任医师、副主任医师。
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
    suggestions = _prefer_same_doctor_pairs(suggestions, request)
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
    for group in sorted(
        _demand_groups(demands),
        key=lambda item: (
            not _is_weekday(item["work_date"]),
            -_group_expected_visits(item),
            item["work_date"],
            item["room_id"],
        ),
    ):
        morning = group["morning"]
        afternoon = group["afternoon"]
        if morning is not None and afternoon is not None:
            paired_available = [
                candidate
                for candidate in _available_candidates(candidates, morning, assigned_doctor_slots, assigned_room_slots)
                if (
                    not _slot_conflicts(assigned_doctor_slots, candidate.doctor_id, afternoon.work_date, afternoon.period)
                    and (not candidate.room_id or not _slot_conflicts(assigned_room_slots, candidate.room_id, afternoon.work_date, afternoon.period))
                    and not _is_unavailable(candidate, afternoon.work_date, afternoon.period)
                )
            ]
            selected = _select_candidate(paired_available, morning, assigned_counts)
            if selected is not None:
                _add_suggestion(suggestions, assigned_counts, assigned_doctor_slots, assigned_room_slots, selected, morning)
                _add_suggestion(suggestions, assigned_counts, assigned_doctor_slots, assigned_room_slots, selected, afternoon)
                continue
        for demand in (morning, afternoon):
            if demand is None:
                continue
            selected = _select_candidate(
                _available_candidates(candidates, demand, assigned_doctor_slots, assigned_room_slots),
                demand,
                assigned_counts,
            )
            if selected is not None:
                _add_suggestion(suggestions, assigned_counts, assigned_doctor_slots, assigned_room_slots, selected, demand)
    return ScheduleSuggestionResponse(
        aiRecordId=f"ai-schedule-record-{uuid4()}",
        suggestions=suggestions,
        knowledgeSources=[],
        backgroundSummary=_background_summary(request),
        fallbackUsed=fallback_used,
    )


def _demand_groups(demands):
    groups = {}
    for demand in demands:
        key = (demand.department_id, demand.room_id or "", demand.work_date)
        group = groups.setdefault(
            key,
            {
                "department_id": demand.department_id,
                "room_id": demand.room_id or "",
                "work_date": demand.work_date,
                "morning": None,
                "afternoon": None,
            },
        )
        if demand.period == "上午":
            group["morning"] = _better_demand(group["morning"], demand)
        elif demand.period == "下午":
            group["afternoon"] = _better_demand(group["afternoon"], demand)
    return list(groups.values())


def _better_demand(current, candidate):
    if current is None:
        return candidate
    current_history = current.historical_visits if current.historical_visits is not None else 0
    candidate_history = candidate.historical_visits if candidate.historical_visits is not None else 0
    if (candidate.expected_visits, candidate_history) > (current.expected_visits, current_history):
        return candidate
    return current


def _group_expected_visits(group) -> int:
    return sum(demand.expected_visits for demand in (group["morning"], group["afternoon"]) if demand is not None)


def _available_candidates(candidates, demand, assigned_doctor_slots, assigned_room_slots):
    return [
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


def _select_candidate(candidates, demand, assigned_counts):
    if not candidates:
        return None
    weekday = _is_weekday(demand.work_date)
    return sorted(
        candidates,
        key=lambda item: (
            assigned_counts.get(item.doctor_id, 0),
            -_title_priority(item) if weekday else 0,
            -_doctor_demand_score(item) if weekday else _doctor_demand_score(item),
            -item.weekly_capacity,
            item.doctor_name,
        ),
    )[0]


def _add_suggestion(suggestions, assigned_counts, assigned_doctor_slots, assigned_room_slots, selected, demand) -> None:
    assigned_counts[selected.doctor_id] = assigned_counts.get(selected.doctor_id, 0) + 1
    _reserve_slot(assigned_doctor_slots, selected.doctor_id, demand.work_date, demand.period)
    if selected.room_id:
        _reserve_slot(assigned_room_slots, selected.room_id, demand.work_date, demand.period)
    suggestions.append(_suggestion_from_candidate(selected, demand))


def _suggestion_from_candidate(selected, demand) -> ScheduleSuggestion:
    baseline = demand.historical_visits if demand.historical_visits is not None else demand.expected_visits
    capacity = max(8, min(60, int(max(demand.expected_visits, baseline) * 1.15)))
    return ScheduleSuggestion(
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


def _prefer_same_doctor_pairs(
    suggestions: list[ScheduleSuggestion],
    request: ScheduleSuggestionRequest,
) -> list[ScheduleSuggestion]:
    result = list(suggestions)
    groups: dict[tuple[str, str, str], dict[str, tuple[int, ScheduleSuggestion]]] = {}
    for index, suggestion in enumerate(result):
        if suggestion.period not in VALID_PERIODS:
            continue
        key = (suggestion.department_id, suggestion.room_id or "", suggestion.work_date)
        groups.setdefault(key, {})[suggestion.period] = (index, suggestion)

    for (department_id, room_id, work_date), group in groups.items():
        morning_pair = group.get("上午")
        afternoon_pair = group.get("下午")
        if morning_pair is None or afternoon_pair is None:
            continue
        morning_index, morning_suggestion = morning_pair
        afternoon_index, afternoon_suggestion = afternoon_pair
        if morning_suggestion.doctor_id == afternoon_suggestion.doctor_id:
            continue
        morning_demand = _matching_demand(request.demands, department_id, room_id, work_date, "上午")
        afternoon_demand = _matching_demand(request.demands, department_id, room_id, work_date, "下午")
        if morning_demand is None or afternoon_demand is None:
            continue

        assigned_counts: dict[str, int] = {}
        assigned_doctor_slots: set[tuple[str, str, str]] = set()
        assigned_room_slots: set[tuple[str, str, str]] = set()
        for index, suggestion in enumerate(result):
            if index in {morning_index, afternoon_index}:
                continue
            assigned_counts[suggestion.doctor_id] = assigned_counts.get(suggestion.doctor_id, 0) + 1
            _reserve_slot(assigned_doctor_slots, suggestion.doctor_id, suggestion.work_date, suggestion.period)
            if suggestion.room_id:
                _reserve_slot(assigned_room_slots, suggestion.room_id, suggestion.work_date, suggestion.period)

        paired_available = [
            candidate
            for candidate in _available_candidates(request.candidates, morning_demand, assigned_doctor_slots, assigned_room_slots)
            if (
                not _slot_conflicts(assigned_doctor_slots, candidate.doctor_id, afternoon_demand.work_date, afternoon_demand.period)
                and (not candidate.room_id or not _slot_conflicts(assigned_room_slots, candidate.room_id, afternoon_demand.work_date, afternoon_demand.period))
                and not _is_unavailable(candidate, afternoon_demand.work_date, afternoon_demand.period)
            )
        ]
        selected = _select_candidate(paired_available, morning_demand, assigned_counts)
        if selected is None:
            continue
        result[morning_index] = _suggestion_from_candidate(selected, morning_demand)
        result[afternoon_index] = _suggestion_from_candidate(selected, afternoon_demand)
    return result


def _matching_demand(demands, department_id: str, room_id: str, work_date: str, period: str):
    return next(
        (
            demand
            for demand in demands
            if (
                demand.department_id == department_id
                and demand.work_date == work_date
                and demand.period == period
                and (not demand.room_id or demand.room_id == room_id)
            )
        ),
        None,
    )


def _is_unavailable(candidate, work_date: str, period: str) -> bool:
    return _has_unavailable_slot(candidate, work_date, period)


def _has_unavailable_slot(candidate, work_date: str, period: str) -> bool:
    for slot in candidate.unavailable_slots:
        slot_date = str(slot.get("date", ""))
        slot_period = _normalize_period(slot.get("period", ""))
        if slot_date != work_date:
            continue
        if slot_period == period:
            return True
    return False


def _normalize_period(value) -> str:
    text = str(value or "").strip()
    upper_text = text.upper()
    if upper_text == "MORNING":
        return "\u4e0a\u5348"
    if upper_text == "AFTERNOON":
        return "\u4e0b\u5348"
    return text


def _period_order(period: str) -> int:
    return {"上午": 0, "下午": 1}.get(period, 9)


def _is_weekday(work_date: str) -> bool:
    try:
        from datetime import date

        return date.fromisoformat(work_date).weekday() < 5
    except ValueError:
        return True


def _doctor_demand_score(candidate) -> int:
    return max(candidate.historical_average_visits, candidate.weekly_capacity)


def _title_priority(candidate) -> int:
    title = (candidate.title or "").strip()
    if "副主任" in title:
        return 1
    if "主任" in title:
        return 2
    return 0


def _prefer_senior_on_weekday_peak(demand) -> bool:
    return _is_weekday(demand.work_date) and demand.expected_visits >= 30


def _slot_conflicts(assigned_slots: set[tuple[str, str, str]], owner_id: str, work_date: str, period: str) -> bool:
    if not owner_id:
        return False
    return (owner_id, work_date, period) in assigned_slots


def _reserve_slot(assigned_slots: set[tuple[str, str, str]], owner_id: str, work_date: str, period: str) -> None:
    if owner_id:
        assigned_slots.add((owner_id, work_date, period))


def _background_summary(request: ScheduleSuggestionRequest) -> str:
    return request.background_summary
