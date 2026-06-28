from app.clinical_assistance.models import ClinicalKnowledgeSource
from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ExecutorCandidate, TriageRequest, TriageResponse

_SYSTEM_PROMPT = """
你是医院 HIS 系统的医技分诊调度模块，负责将患者的检查/检验/处置申请分配到最合适的执行诊室。

分配原则：
1. 优先匹配诊室专长与申请项目（specialties 字段）
2. 兼顾各诊室当前排队负载（currentLoad / capacity），避免资源过度集中
3. EMERGENCY 申请优先选负载最低且具备相应设备的诊室
4. 综合考虑诊室位置和设备情况

请严格输出 JSON 对象，包含以下字段：
- doctorId   : 所选诊室的 ID（必填，必须是 candidates 列表中存在的 doctorId）
- doctorName : 所选诊室名称
- location   : 诊室位置
- equipmentId: 使用的设备 ID（无设备时为 null）
- reasons    : 字符串数组，2-3 条简明扼要的选择理由
"""


def dispatch(request: TriageRequest) -> TriageResponse:
    config = settings()
    available = [c for c in request.candidates if c.available]
    if not available:
        raise ValueError("没有可用的执行诊室")

    knowledge_sources = _knowledge_sources(request)

    if config.llm_enabled:
        try:
            return _dispatch_with_llm(request, available, knowledge_sources, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc

    return _dispatch_by_rule(request, available, knowledge_sources, fallback_used=config.llm_enabled)


def _dispatch_with_llm(
    request: TriageRequest,
    available: list[ExecutorCandidate],
    knowledge_sources: list[ClinicalKnowledgeSource],
    config,
) -> TriageResponse:
    result = chat_json(
        config,
        system_prompt=_SYSTEM_PROMPT,
        user_payload={
            "orderId": request.order_id,
            "projectType": request.project_type,
            "bodyPart": request.body_part or "",
            "requiredSpecialty": request.required_specialty or "",
            "urgency": request.urgency,
            "candidates": [
                {
                    "doctorId": c.doctor_id,
                    "doctorName": c.doctor_name,
                    "specialties": c.specialties,
                    "currentLoad": c.current_load,
                    "capacity": c.capacity,
                    "location": c.location,
                    "equipmentIds": c.equipment_ids,
                }
                for c in available
            ],
            "knowledgeContext": [
                {"title": s.title, "content": s.content} for s in knowledge_sources[:2]
            ],
        },
    )

    payload = extract_json_object(result.content)
    doctor_id = payload.get("doctorId") or payload.get("workspaceId")
    if not doctor_id:
        raise ValueError("LLM 分诊响应缺少 doctorId")

    matched = next((c for c in available if c.doctor_id == doctor_id), None)
    if matched is None:
        raise ValueError(f"LLM 选择了不存在的诊室 ID: {doctor_id}")

    reasons = payload.get("reasons", [])
    if isinstance(reasons, str):
        reasons = [reasons]

    return TriageResponse(
        orderId=request.order_id,
        doctorId=matched.doctor_id,
        doctorName=payload.get("doctorName") or matched.doctor_name,
        location=payload.get("location") or matched.location,
        equipmentId=payload.get("equipmentId") or (matched.equipment_ids[0] if matched.equipment_ids else None),
        score=0.0,
        reasons=reasons or ["AI 综合调度分配"],
        knowledgeSources=knowledge_sources,
    )


def _dispatch_by_rule(
    request: TriageRequest,
    available: list[ExecutorCandidate],
    knowledge_sources: list[ClinicalKnowledgeSource],
    fallback_used: bool = False,
) -> TriageResponse:
    ranked = []
    for candidate in available:
        specialty_points, specialty_matched = _specialty_score(candidate, request)
        load_ratio = min(candidate.current_load / candidate.capacity, 1.0)
        load_points = (1.0 - load_ratio) * 40.0
        equipment_points = 10.0 if candidate.equipment_ids else 0.0
        ranked.append((specialty_points + load_points + equipment_points, candidate, specialty_matched, load_ratio))

    score, selected, specialty_matched, load_ratio = max(
        ranked, key=lambda item: (item[0], -item[3], item[1].doctor_id)
    )

    reasons = []
    if fallback_used:
        reasons.append("AI 大模型不可用，已自动切换为规则调度")
    reasons += [
        "诊室专长与项目匹配" if specialty_matched else "当前无完全匹配专长，按负载择优分配",
        f"当前负载 {selected.current_load}/{selected.capacity}",
        "急诊优先入队" if request.urgency.upper() == "EMERGENCY" else "按常规优先级入队",
    ]
    if knowledge_sources:
        reasons.append(f"参考本院知识：{knowledge_sources[0].title}")

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


def _specialty_score(candidate: ExecutorCandidate, request: TriageRequest) -> tuple[float, bool]:
    target = (request.required_specialty or request.body_part or request.project_type).strip().lower()
    if not target:
        return 20.0, False
    matched = any(target in item.lower() or item.lower() in target for item in candidate.specialties)
    return (50.0 if matched else 0.0), matched


def _knowledge_sources(request: TriageRequest) -> list[ClinicalKnowledgeSource]:
    query = " ".join(
        v for v in [
            request.project_type,
            request.body_part or "",
            request.required_specialty or "",
            request.urgency,
            "医技 分诊 急诊 设备 队列",
        ] if v
    )
    return [
        ClinicalKnowledgeSource(
            sourceId=s.source_id,
            sourceType=s.source_type,
            businessId=s.business_id,
            title=s.title,
            content=s.content,
            score=s.score,
        )
        for s in retrieve(query, limit=4)
    ]
