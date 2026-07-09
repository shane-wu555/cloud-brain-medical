from app.core.rag import search_memory
from app.schedule_suggestions.models import DoctorCandidate, ScheduleDemand, ScheduleSuggestion, ScheduleSuggestionRequest
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


def test_schedule_mock_pairs_morning_and_afternoon_when_possible(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(doctorId="doctor-1", doctorName="张医生", departmentId="dept-general", roomId="room-1"),
            DoctorCandidate(doctorId="doctor-2", doctorName="李医生", departmentId="dept-general", roomId="room-1"),
        ],
        demands=[
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="上午"),
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="下午"),
        ],
    )

    response = schedule_service._mock_suggest(request)

    assert len(response.suggestions) == 2
    assert {item.period for item in response.suggestions} == {"上午", "下午"}
    assert len({item.doctor_id for item in response.suggestions}) == 1


def test_schedule_llm_suggestions_are_repaired_to_same_day_pairs(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(doctorId="doctor-1", doctorName="张医生", departmentId="dept-general", roomId="room-1"),
            DoctorCandidate(doctorId="doctor-2", doctorName="李医生", departmentId="dept-general", roomId="room-1"),
        ],
        demands=[
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="上午"),
            ScheduleDemand(departmentId="dept-general", roomId="room-1", workDate="2026-07-08", period="下午"),
        ],
    )
    split_suggestions = [
        ScheduleSuggestion(
            suggestionId="s1",
            doctorId="doctor-1",
            doctorName="张医生",
            departmentId="dept-general",
            roomId="room-1",
            roomName="诊室一",
            workDate="2026-07-08",
            period="上午",
            capacity=20,
        ),
        ScheduleSuggestion(
            suggestionId="s2",
            doctorId="doctor-2",
            doctorName="李医生",
            departmentId="dept-general",
            roomId="room-1",
            roomName="诊室一",
            workDate="2026-07-08",
            period="下午",
            capacity=20,
        ),
    ]

    repaired = schedule_service._prefer_same_doctor_pairs(split_suggestions, request)

    assert len({item.doctor_id for item in repaired}) == 1
    assert {item.period for item in repaired} == {"上午", "下午"}


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


def test_schedule_mock_prioritizes_senior_titles_on_weekday_peak(monkeypatch):
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    request = ScheduleSuggestionRequest(
        candidates=[
            DoctorCandidate(
                doctorId="doctor-chief",
                doctorName="主任医生",
                title="主任医师",
                departmentId="dept-general",
                roomId="room-1",
                weeklyCapacity=40,
                historicalAverageVisits=20,
            ),
            DoctorCandidate(
                doctorId="doctor-attending",
                doctorName="普通医生",
                title="主治医师",
                departmentId="dept-general",
                roomId="room-1",
                weeklyCapacity=40,
                historicalAverageVisits=20,
            ),
        ],
        demands=[
            ScheduleDemand(
                departmentId="dept-general",
                roomId="room-1",
                workDate="2026-07-06",
                period="上午",
                expectedVisits=35,
            )
        ],
    )

    response = schedule_service._mock_suggest(request)

    assert response.suggestions
    assert response.suggestions[0].doctor_id == "doctor-chief"
