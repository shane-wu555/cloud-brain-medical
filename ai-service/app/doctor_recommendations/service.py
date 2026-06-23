from uuid import uuid4

from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import (
    DoctorCandidate,
    DoctorRecommendationItem,
    DoctorRecommendationRequest,
    DoctorRecommendationResponse,
)


def recommend_doctors(request: DoctorRecommendationRequest) -> DoctorRecommendationResponse:
    config = settings()
    if config.llm_enabled:
        try:
            return _recommend_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_recommend(request, fallback_used=config.llm_enabled)


def _recommend_with_llm(request: DoctorRecommendationRequest, config) -> DoctorRecommendationResponse:
    sources = _knowledge_sources(request)
    eligible = _eligible_candidates(request)
    result = chat_json(
        config,
        system_prompt="""
你是医院小程序医生推荐模块。只能推荐存在可用排班和剩余号源的候选医生。
请严格输出 JSON 对象，字段为 recommendations。
recommendations 最多 5 项，每项包含 doctorId、doctorName、departmentId、departmentName、score、reason、source。
score 为 0-100。推荐理由必须结合症状、医生擅长、风险等级、号源和 providedKnowledgeSources。
不得推荐 candidates 中不存在或 available=false 或 remainingSlots<=0 的医生。
""",
        user_payload={
            **request.model_dump(by_alias=True),
            "eligibleCandidates": [candidate.model_dump(by_alias=True) for candidate in eligible],
            "providedKnowledgeSources": [source.model_dump(by_alias=True) for source in sources],
        },
    )
    payload = extract_json_object(result.content)
    by_id = {candidate.doctor_id: candidate for candidate in eligible}
    recommendations: list[DoctorRecommendationItem] = []
    for item in payload.get("recommendations", []):
        if not isinstance(item, dict) or item.get("doctorId") not in by_id:
            continue
        candidate = by_id[item["doctorId"]]
        recommendations.append(
            DoctorRecommendationItem(
                doctorId=candidate.doctor_id,
                doctorName=candidate.doctor_name,
                departmentId=candidate.department_id,
                departmentName=candidate.department_name,
                score=float(item.get("score", 0)),
                reason=item.get("reason", "结合症状和可用号源推荐，需患者自主确认。"),
                source=item.get("source") or (sources[0].source_id if sources else "AI"),
            )
        )
    if not recommendations:
        raise ValueError("LLM response contains no eligible recommendations")
    return DoctorRecommendationResponse(
        aiRecordId=f"ai-doctor-rec-{uuid4()}",
        recommendations=recommendations[:5],
        knowledgeSources=sources,
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
    )


def _mock_recommend(request: DoctorRecommendationRequest, fallback_used: bool = False) -> DoctorRecommendationResponse:
    sources = _knowledge_sources(request)
    eligible = _eligible_candidates(request)
    ranked = sorted(
        eligible,
        key=lambda candidate: (_score(candidate, request), candidate.remaining_slots, candidate.title),
        reverse=True,
    )
    recommendations = [
        DoctorRecommendationItem(
            doctorId=candidate.doctor_id,
            doctorName=candidate.doctor_name,
            departmentId=candidate.department_id,
            departmentName=candidate.department_name,
            score=round(_score(candidate, request), 2),
            reason=_reason(candidate, request, sources),
            source=sources[0].source_id if sources else "RULE",
        )
        for candidate in ranked[:5]
    ]
    return DoctorRecommendationResponse(
        aiRecordId=f"ai-doctor-rec-{uuid4()}",
        recommendations=recommendations,
        knowledgeSources=sources,
        fallbackUsed=fallback_used,
    )


def _eligible_candidates(request: DoctorRecommendationRequest) -> list[DoctorCandidate]:
    return [
        candidate
        for candidate in request.candidates
        if candidate.available and candidate.remaining_slots > 0
    ]


def _score(candidate: DoctorCandidate, request: DoctorRecommendationRequest) -> float:
    text = " ".join([request.symptoms, *request.symptom_tags]).lower()
    specialty_text = " ".join(candidate.specialties).lower()
    score = min(candidate.remaining_slots, 20) * 1.5
    if request.preferred_department_id and candidate.department_id == request.preferred_department_id:
        score += 25
    if any(tag.lower() in specialty_text or specialty.lower() in text for tag in request.symptom_tags for specialty in candidate.specialties):
        score += 35
    if any(word in specialty_text for word in ("头痛", "眩晕", "脑血管", "神经")) and any(word in text for word in ("头痛", "眩晕", "抽搐", "意识")):
        score += 30
    if request.risk_level.upper() == "HIGH" and candidate.remaining_slots > 0:
        score += 10
    if "主任" in candidate.title:
        score += 8
    elif "副主任" in candidate.title:
        score += 6
    return min(score, 100.0)


def _reason(candidate: DoctorCandidate, request: DoctorRecommendationRequest, sources: list[ClinicalKnowledgeSource]) -> str:
    source = sources[0].title if sources else "本院规则"
    return (
        f"{candidate.doctor_name}擅长{ '、'.join(candidate.specialties) or candidate.department_name }，"
        f"当前剩余号源 {candidate.remaining_slots}；结合风险等级 {request.risk_level} 和来源“{source}”推荐。"
    )


def _knowledge_sources(request: DoctorRecommendationRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        value
        for value in [request.symptoms, *request.symptom_tags, request.preferred_department_id or "", "医生 擅长 科室 号源"]
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
        for source in retrieve(query, limit=5)
    ]
