from app.clinical_assistance.models import ClinicalAssistanceRequest
from app.clinical_assistance.service import assist
from app.consultation.models import ConsultationMessageRequest, ConsultationRequest
from app.consultation.service import consult, continue_consultation
from app.core.json_utils import extract_json_object
from app.core.rag import embed, search_memory
from app.ct_analysis.models import CtAnalysisRequest
from app.ct_analysis.service import get, retry, submit
from app.doctor_recommendations.models import DoctorCandidate, DoctorRecommendationRequest
from app.doctor_recommendations.service import recommend_doctors
from app.prescription_suggestions.models import PrescriptionSuggestionRequest
from app.prescription_suggestions.service import suggest_prescription
from app.report_drafts.models import ReportDraftRequest
from app.report_drafts.service import create_draft
from app.schedule_suggestions.models import DoctorCandidate as ScheduleDoctorCandidate
from app.schedule_suggestions.models import ScheduleDemand, ScheduleSuggestionRequest
from app.schedule_suggestions.service import suggest
from app.triage.models import ExecutorCandidate, TriageRequest
from app.triage.service import dispatch
import time


def test_extract_json_object_from_markdown_fence():
    payload = extract_json_object('```json\n{"suggestions":[]}\n```')
    assert payload == {"suggestions": []}


def test_clinical_assistance_mock_response_requires_human_confirmation(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = assist(
        ClinicalAssistanceRequest(
            appointmentId="appt-1",
            patientId="patient-1",
            chiefComplaint="头痛伴眩晕",
            presentIllness="持续 2 天",
            prompt="给出检查建议",
        )
    )

    assert response.created_by_type == "AI"
    assert response.requires_human_confirmation is True
    assert response.provider == "mock"
    assert response.suggestions
    assert response.knowledge_sources
    assert response.suggestions[0].source == response.knowledge_sources[0].source_id


def test_consultation_mock_keeps_original_contract_and_sources(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = consult(
        ConsultationRequest(
            patientId="patient-1",
            description="突发剧烈头痛伴眩晕",
            symptomTags=["头痛", "剧烈疼痛"],
        )
    )

    assert response.risk_level == "HIGH"
    assert response.suggest_offline_urgent is True
    assert response.recommended_department_id
    assert response.knowledge_sources
    assert response.knowledge_sources[0]["sourceType"]


def test_consultation_session_can_follow_up(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    first = consult(ConsultationRequest(patientId="patient-1", description="头痛", symptomTags=["头痛"]))

    assert first.consultation_id
    assert first.needs_follow_up is True
    assert first.follow_up_questions

    second = continue_consultation(
        first.consultation_id,
        ConsultationMessageRequest(message="右侧头痛持续 2 天，无抽搐和意识异常", symptomTags=[]),
    )

    assert second.consultation_id == first.consultation_id
    assert "右侧头痛持续 2 天" in second.summary
    assert second.knowledge_sources


def test_report_draft_is_not_formal_report(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = create_draft(
        ReportDraftRequest(
            orderId="order-1",
            reportType="CHECK",
            projectName="头部 CT",
            findings="未见明确急性出血征象",
        )
    )

    assert response.created_by_type == "AI"
    assert response.requires_human_confirmation is True
    assert response.knowledge_sources
    assert "草稿" in response.safety_notice


def test_prescription_suggestion_uses_sources_and_requires_confirmation(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = suggest_prescription(
        PrescriptionSuggestionRequest(
            appointmentId="appt-1",
            patientId="patient-1",
            diagnosis="脑血管病风险评估",
            chiefComplaint="头痛",
            allergyHistory="青霉素过敏",
            prompt="给出用药安全提醒",
        )
    )

    assert response.created_by_type == "AI"
    assert response.requires_human_confirmation is True
    assert response.knowledge_sources
    assert response.warnings


def test_doctor_recommendations_filter_unavailable_and_use_sources(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = recommend_doctors(
        DoctorRecommendationRequest(
            patientId="patient-1",
            symptoms="头痛眩晕",
            symptomTags=["头痛"],
            riskLevel="MEDIUM",
            preferredDepartmentId="dept-neuro",
            candidates=[
                DoctorCandidate(
                    doctorId="doctor-001",
                    doctorName="张医生",
                    departmentId="dept-neuro",
                    departmentName="神经内科",
                    title="主任医师",
                    specialties=["头痛", "脑血管疾病"],
                    available=True,
                    remainingSlots=5,
                ),
                DoctorCandidate(
                    doctorId="doctor-002",
                    doctorName="李医生",
                    departmentId="dept-neuro",
                    departmentName="神经内科",
                    specialties=["癫痫"],
                    available=False,
                    remainingSlots=10,
                ),
            ],
        )
    )

    assert len(response.recommendations) == 1
    assert response.recommendations[0].doctor_id == "doctor-001"
    assert response.knowledge_sources


def test_triage_response_includes_rag_sources(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = dispatch(
        TriageRequest(
            orderId="order-1",
            projectType="CHECK",
            bodyPart="头部",
            requiredSpecialty="头部",
            urgency="EMERGENCY",
            candidates=[
                ExecutorCandidate(
                    doctorId="doctor-ct",
                    doctorName="检查医生",
                    specialties=["头部"],
                    currentLoad=1,
                    capacity=5,
                    available=True,
                    location="CT室",
                    equipmentIds=["ct-1"],
                )
            ],
        )
    )

    assert response.doctor_id == "doctor-ct"
    assert response.knowledge_sources
    assert any("参考本院来源" in reason for reason in response.reasons)


def test_ct_analysis_generates_report_draft_and_sources(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    task_id = submit(CtAnalysisRequest(orderId="order-ct", objectKey="ct/sample.dcm", clinicalContext="突发头痛"))
    time.sleep(0.3)
    task = get(task_id)

    assert task is not None
    assert task["status"] == "COMPLETED"
    assert task["progress"] == 100
    assert task["requiresHumanConfirmation"] is True
    assert task["knowledgeSources"]
    assert task["result"]["reportDraft"]["requiresHumanConfirmation"] is True


def test_ct_analysis_failed_task_can_retry(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    task_id = submit(CtAnalysisRequest(orderId="order-ct", objectKey="ct/fail.dcm"))
    time.sleep(0.3)
    assert get(task_id)["status"] == "FAILED"

    retry(task_id)
    assert get(task_id)["retryCount"] == 1


def test_schedule_suggestions_do_not_require_sources_and_need_admin_confirmation(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    response = suggest(
        ScheduleSuggestionRequest(
            candidates=[
                ScheduleDoctorCandidate(
                        doctorId="doctor-001",
                        doctorName="张医生",
                        departmentId="dept-neuro",
                        specialty="头痛与脑血管疾病",
                        weeklyCapacity=40,
                    )
                ],
                demands=[
                ScheduleDemand(
                    departmentId="dept-neuro",
                        workDate="2026-06-24",
                        period="上午",
                        expectedVisits=20,
                        historicalVisits=30,
                    )
                ],
            )
    )

    assert response.suggestions
    assert response.suggestions[0].requires_admin_confirmation is True
    assert response.knowledge_sources == []


def test_rag_embedding_is_stable_and_memory_search_has_sources(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    first = embed("头部 CT 急诊", dim=64)
    second = embed("头部 CT 急诊", dim=64)

    assert first == second
    assert len(first) == 64
    assert search_memory("头部 CT", limit=2)[0].source_type == "HOSPITAL_RULE"
