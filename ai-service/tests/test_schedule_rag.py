from app.core.rag import search_memory
from app.schedule_suggestions.models import DoctorCandidate, ScheduleDemand, ScheduleSuggestionRequest
import app.schedule_suggestions.service as schedule_service


def test_memory_rag_respects_source_type_filter(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    sources = search_memory("排班 管理员确认", limit=5, source_types=("HOSPITAL_RULE",))

    assert sources
    assert {source.source_type for source in sources} == {"HOSPITAL_RULE"}
    assert search_memory("排班", limit=5, source_types=("SCHEDULE",)) == []


def test_schedule_mock_does_not_require_rag_sources(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    response = schedule_service._mock_suggest(ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-1",
                doctorName="张医生",
                departmentId="dept-general",
                roomId="room-1",
                specialty="全科",
            )
        ],
        demands=[
            ScheduleDemand(
                departmentId="dept-general",
                roomId="room-1",
                workDate="2026-07-08",
                period="上午",
                expectedVisits=30,
            )
        ],
    ))

    assert response.suggestions
    assert response.knowledge_sources == []


def test_schedule_mock_uses_slot_level_unavailability(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-1",
                doctorName="张医生",
                departmentId="dept-general",
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


def test_schedule_mock_limits_one_doctor_per_room_period(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(doctorId="doctor-1", doctorName="张医生", departmentId="dept-general", roomId="room-1"),
            DoctorCandidate(doctorId="doctor-2", doctorName="李医生", departmentId="dept-general", roomId="room-1"),
        ],
        demands=[
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="上午"),
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="上午"),
        ],
    )

    response = schedule_service._mock_suggest(request)

    assert len(response.suggestions) == 1
    assert response.suggestions[0].room_id == "room-1"


def test_schedule_mock_prioritizes_high_history_doctor_on_weekdays(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-high",
                doctorName="高专家",
                departmentId="dept-general",
                roomId="room-1",
                weeklyCapacity=80,
                historicalAverageVisits=45,
            ),
            DoctorCandidate(
                doctorId="doctor-low",
                doctorName="普通医生",
                departmentId="dept-general",
                roomId="room-1",
                weeklyCapacity=40,
                historicalAverageVisits=10,
            ),
        ],
        demands=[
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-11", period="上午", expectedVisits=15),
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="上午", expectedVisits=45),
        ],
    )

    response = schedule_service._mock_suggest(request)

    weekday = next(item for item in response.suggestions if item.work_date == "2026-07-08")
    assert weekday.doctor_id == "doctor-high"
