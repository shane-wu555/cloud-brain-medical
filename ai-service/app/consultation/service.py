from dataclasses import dataclass, field
from threading import Lock
from uuid import uuid4

from app.core.config import settings
from app.core.json_utils import extract_json_object
from app.core.llm import LlmError, chat_json, invalid_llm_output
from app.core.rag import retrieve

from .models import ConsultationMessageRequest, ConsultationRequest, ConsultationResponse, DoctorRecommendation


@dataclass
class ConsultationSession:
    consultation_id: str
    patient_id: str | None
    descriptions: list[str] = field(default_factory=list)
    symptom_tags: list[str] = field(default_factory=list)


_sessions: dict[str, ConsultationSession] = {}
_session_lock = Lock()


def consult(request: ConsultationRequest) -> ConsultationResponse:
    consultation_id = _create_session(request)
    response = _consult(request)
    return response.model_copy(update={"consultation_id": consultation_id})


def continue_consultation(consultation_id: str, request: ConsultationMessageRequest) -> ConsultationResponse:
    with _session_lock:
        session = _sessions.get(consultation_id)
        if session is None:
            raise ValueError("问诊会话不存在或已过期")
        if request.message.strip():
            session.descriptions.append(request.message.strip())
        for tag in request.symptom_tags:
            if tag not in session.symptom_tags:
                session.symptom_tags.append(tag)
        merged = ConsultationRequest(
            patientId=session.patient_id,
            description="；".join(session.descriptions),
            symptomTags=session.symptom_tags,
        )
    response = _consult(merged)
    return response.model_copy(update={"consultation_id": consultation_id})


def _consult(request: ConsultationRequest) -> ConsultationResponse:
    config = settings()
    if config.llm_enabled:
        try:
            return _consult_with_llm(request, config)
        except (LlmError, ValueError, KeyError) as exc:
            if not config.allow_fallback:
                if isinstance(exc, LlmError):
                    raise
                raise invalid_llm_output(exc) from exc
    return _mock_consult(request, fallback_used=config.llm_enabled)


def _consult_with_llm(request: ConsultationRequest, config) -> ConsultationResponse:
    sources = _sources(request)
    system_prompt = """
你是医院微信小程序的 AI 智能问诊模块，只做就诊前信息整理和分诊建议。
请严格输出 JSON 对象，字段包括 summary、riskLevel、recommendedDepartmentId、recommendedDepartmentName、recommendedDoctors、suggestOfflineUrgent、recordDraft。
也可以输出 needsFollowUp 和 followUpQuestions；当病程、部位、伴随症状、危险信号不清楚时应继续追问。
riskLevel 只能是 LOW、MEDIUM、HIGH。
recommendedDoctors 最多 3 个，每项包含 doctorId、doctorName、reason；如果无法确定医生，可返回空数组。
如果出现意识障碍、抽搐、突发剧烈头痛、偏瘫、言语不清等危险信号，riskLevel 必须为 HIGH，suggestOfflineUrgent 必须为 true。
不要输出最终诊断，不要给出生效处方。
"""
    result = chat_json(
        config,
        system_prompt=system_prompt,
        user_payload={
            "patientId": request.patient_id,
            "description": request.description,
            "symptomTags": request.symptom_tags,
            "providedKnowledgeSources": sources,
        },
    )
    payload = extract_json_object(result.content)
    return ConsultationResponse(
        aiRecordId=f"ai-consult-{uuid4()}",
        summary=payload["summary"],
        riskLevel=payload["riskLevel"],
        recommendedDepartmentId=payload.get("recommendedDepartmentId") or "dept-general",
        recommendedDepartmentName=payload.get("recommendedDepartmentName") or "全科医学",
        recommendedDoctors=[
            DoctorRecommendation(
                doctorId=item.get("doctorId", ""),
                doctorName=item.get("doctorName", ""),
                reason=item.get("reason", "由 AI 根据症状和排班建议推荐，需患者自主确认。"),
            )
            for item in payload.get("recommendedDoctors", [])
            if isinstance(item, dict)
        ][:3],
        suggestOfflineUrgent=bool(payload.get("suggestOfflineUrgent")),
        needsFollowUp=bool(payload.get("needsFollowUp")),
        followUpQuestions=[str(item) for item in payload.get("followUpQuestions", [])][:4],
        recordDraft=payload["recordDraft"],
        provider=result.provider,
        model=result.model,
        fallbackUsed=False,
        knowledgeSources=sources,
    )


def _mock_consult(request: ConsultationRequest, fallback_used: bool = False) -> ConsultationResponse:
    text = request.description.lower()
    tags = set(request.symptom_tags)
    neurological = bool(tags & {"头痛", "眩晕"}) or any(word in text for word in ("头痛", "眩晕"))
    urgent = bool(tags & {"剧烈疼痛"}) or any(word in text for word in ("昏迷", "抽搐", "意识不清"))
    department_id = "dept-neuro" if neurological or urgent else "dept-general"
    department_name = "神经内科" if department_id == "dept-neuro" else "全科医学"
    doctors = [
        DoctorRecommendation(doctorId="doctor-001", doctorName="张医生", reason="擅长头痛、眩晕与脑血管疾病")
        if department_id == "dept-neuro"
        else DoctorRecommendation(doctorId="doctor-003", doctorName="陈医生", reason="适合常见病和慢病初诊")
    ]
    risk = "HIGH" if urgent else "MEDIUM" if neurological else "LOW"
    questions = _follow_up_questions(request, risk)
    return ConsultationResponse(
        aiRecordId=f"ai-consult-{uuid4()}",
        summary=f"症状摘要：{request.description}" if request.description else "症状摘要：待补充",
        riskLevel=risk,
        recommendedDepartmentId=department_id,
        recommendedDepartmentName=department_name,
        recommendedDoctors=doctors,
        suggestOfflineUrgent=urgent,
        needsFollowUp=bool(questions) and not urgent,
        followUpQuestions=questions,
        recordDraft=f"AI 问诊初稿，需由门诊医生复核：{request.description}" if request.description else "AI 问诊初稿：患者症状信息不足，需继续追问。",
        fallbackUsed=fallback_used,
        knowledgeSources=_sources(request),
    )


def _sources(request: ConsultationRequest) -> list[dict]:
    query = " ".join([request.description, *request.symptom_tags])
    return [
        {
            "sourceId": source.source_id,
            "sourceType": source.source_type,
            "businessId": source.business_id,
            "title": source.title,
            "content": source.content,
            "score": source.score,
        }
        for source in retrieve(query)
    ]


def _create_session(request: ConsultationRequest) -> str:
    consultation_id = f"consult-{uuid4()}"
    with _session_lock:
        _sessions[consultation_id] = ConsultationSession(
            consultation_id=consultation_id,
            patient_id=request.patient_id,
            descriptions=[request.description.strip()] if request.description.strip() else [],
            symptom_tags=list(dict.fromkeys(request.symptom_tags)),
        )
    return consultation_id


def _follow_up_questions(request: ConsultationRequest, risk: str) -> list[str]:
    if risk == "HIGH":
        return []
    text = request.description
    questions: list[str] = []
    if len(text.strip()) < 12:
        questions.append("请补充症状开始时间、持续多久，以及是否突然加重？")
    if not any(word in text for word in ("左侧", "右侧", "头部", "胸部", "腹部", "四肢", "眩晕", "头痛")):
        questions.append("请说明主要不适部位，以及是否伴随头痛、眩晕、恶心或肢体无力？")
    if not any(word in text for word in ("发热", "呕吐", "抽搐", "意识", "偏瘫", "言语", "外伤")):
        questions.append("是否存在发热、呕吐、抽搐、意识异常、肢体无力、言语不清或近期外伤？")
    return questions[:3]
