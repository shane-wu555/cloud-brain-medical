from app.core.rag import KnowledgeSource, search_memory
from app.schedule_suggestions.models import DoctorCandidate, ScheduleDemand, ScheduleSuggestionRequest
import app.schedule_suggestions.service as schedule_service


def test_memory_rag_respects_source_type_filter(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    sources = search_memory("排班 管理员确认", limit=5, source_types=("HOSPITAL_RULE",))

    assert sources
    assert {source.source_type for source in sources} == {"HOSPITAL_RULE"}
    assert search_memory("排班", limit=5, source_types=("SCHEDULE",)) == []


def test_schedule_suggestions_use_schedule_rag_source_types(monkeypatch):
    captured = {}

    def fake_retrieve(query, limit=3, source_types=None):
        captured["query"] = query
        captured["limit"] = limit
        captured["source_types"] = source_types
        return [
            KnowledgeSource(
                source_id="schedule-test-1",
                source_type="SCHEDULE",
                business_id="schedule-1",
                title="排班：张医生",
                content="医生 张医生，科室 全科医学，日期 2026-07-08，时段 上午。",
                score=0.9,
            )
        ]

    monkeypatch.setattr(schedule_service, "retrieve", fake_retrieve)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-1",
                doctorName="张医生",
                departmentId="dept-general",
                specialty="全科",
            )
        ],
        demands=[
            ScheduleDemand(
                departmentId="dept-general",
                workDate="2026-07-08",
                period="上午",
                expectedVisits=30,
            )
        ],
    )
    sources = schedule_service._knowledge_sources(request)

    assert captured["limit"] == 8
    assert captured["source_types"] == schedule_service.SCHEDULE_RAG_SOURCE_TYPES
    assert "张医生" in captured["query"]
    assert "2026-07-08" in captured["query"]
    assert sources[0].source_type == "SCHEDULE"


def test_schedule_mock_uses_slot_level_unavailability(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-1",
                doctorName="张医生",
                departmentId="dept-general",
                leaveDates=["2026-07-08"],
                unavailableSlots=[{"date": "2026-07-08", "period": "上午", "type": "LEAVE"}],
            )
        ],
        demands=[
            ScheduleDemand(departmentId="dept-general", workDate="2026-07-08", period="上午"),
            ScheduleDemand(departmentId="dept-general", workDate="2026-07-08", period="下午"),
        ],
    )

    response = schedule_service._mock_suggest(request)

    assert len(response.suggestions) == 1
    assert response.suggestions[0].period == "下午"
