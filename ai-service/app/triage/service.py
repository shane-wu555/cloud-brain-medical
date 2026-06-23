from .models import ExecutorCandidate, TriageRequest, TriageResponse
from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.rag import retrieve


def _specialty_score(candidate: ExecutorCandidate, request: TriageRequest) -> tuple[float, bool]:
    target = (request.required_specialty or request.body_part or request.project_type).strip().lower()
    if not target:
        return 20.0, False
    matched = any(target in item.lower() or item.lower() in target for item in candidate.specialties)
    return (50.0 if matched else 0.0), matched


def dispatch(request: TriageRequest) -> TriageResponse:
    knowledge_sources = _knowledge_sources(request)
    available = [candidate for candidate in request.candidates if candidate.available]
    if not available:
        raise ValueError("没有可用的检查/检验执行医生")

    ranked: list[tuple[float, ExecutorCandidate, bool, float]] = []
    for candidate in available:
        specialty_points, specialty_matched = _specialty_score(candidate, request)
        load_ratio = min(candidate.current_load / candidate.capacity, 1.0)
        load_points = (1.0 - load_ratio) * 40.0
        equipment_points = 10.0 if candidate.equipment_ids else 0.0
        ranked.append((specialty_points + load_points + equipment_points, candidate, specialty_matched, load_ratio))

    score, selected, specialty_matched, load_ratio = max(
        ranked, key=lambda item: (item[0], -item[3], item[1].doctor_id)
    )
    reasons = [
        "医生擅长方向与项目匹配" if specialty_matched else "当前无完全匹配专长，按负载择优",
        f"当前负载 {selected.current_load}/{selected.capacity}",
        "急诊优先进入所选医生队列" if request.urgency.upper() == "EMERGENCY" else "按常规优先级进入队列",
    ]
    if knowledge_sources:
        reasons.append(f"参考本院来源：{knowledge_sources[0].title}")
    return TriageResponse(
        orderId=request.order_id,
        doctorId=selected.doctor_id,
        doctorName=selected.doctor_name,
        location=selected.location,
        equipmentId=selected.equipment_ids[0] if selected.equipment_ids else None,
        score=round(score, 2),
        reasons=reasons,
        knowledgeSources=knowledge_sources,
    )


def _knowledge_sources(request: TriageRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        value
        for value in [
            request.project_type,
            request.body_part or "",
            request.required_specialty or "",
            request.urgency,
            "医技 分诊 急诊 设备 队列",
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
        for source in retrieve(query, limit=4)
    ]
