package com.cloudbrain.doctor.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DoctorCatalogRepositoryTest {
    private static final String MORNING = "\u4e0a\u5348";
    private static final String AFTERNOON = "\u4e0b\u5348";

    @Mock
    JdbcTemplate jdbc;

    @Test
    void departmentAndDoctorQueriesDelegateToJdbc() {
        DoctorCatalogRepository repository = new DoctorCatalogRepository(jdbc);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        DoctorCatalogRepository.Doctor doctor = doctor("doctor-1");

        when(jdbc.query(contains("from department where active order by name"), any(RowMapper.class)))
                .thenReturn(List.of(department));
        when(jdbc.query(contains("from department d"), any(RowMapper.class))).thenReturn(List.of(department));
        when(jdbc.query(contains("from staff s"), any(RowMapper.class))).thenReturn(List.of(doctor));
        when(jdbc.query(
                        contains("and s.department_id = ?"),
                        any(RowMapper.class),
                        any()))
                .thenReturn(List.of(doctor));

        assertThat(repository.departments()).containsExactly(department);
        assertThat(repository.schedulingDepartments()).containsExactly(department);
        assertThat(repository.doctors(null, false)).isNotNull();
        assertThat(repository.doctors("dept-1", false)).containsExactly(doctor);
    }

    @Test
    void patientSearchHandlesBlankKeywordAndEscapesPattern() {
        DoctorCatalogRepository repository = new DoctorCatalogRepository(jdbc);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        DoctorCatalogRepository.Doctor doctor = doctor("doctor-1");
        String pattern = "%card!!o!_!%%";

        when(jdbc.query(
                        contains("select distinct d.id, d.name, d.description"),
                        any(RowMapper.class),
                        eq(pattern),
                        eq(pattern),
                        eq(50)))
                .thenReturn(List.of(department));
        when(jdbc.query(
                        contains("select s.id, s.employee_no, s.name, s.title"),
                        any(RowMapper.class),
                        eq(pattern),
                        eq(pattern),
                        eq(pattern),
                        eq(pattern),
                        eq(pattern),
                        eq(pattern),
                        eq(50)))
                .thenReturn(List.of(doctor));

        assertThat(repository.patientSearch(" ", 10).departments()).isEmpty();

        DoctorCatalogRepository.PatientSearchResult result = repository.patientSearch(" card!o_% ", 100);
        assertThat(result.departments()).containsExactly(department);
        assertThat(result.doctors()).containsExactly(doctor);
    }

    @Test
    void createAndUpdateDoctorValidateRoleAndReloadDoctor() {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        DoctorCatalogRepository.Doctor doctor = doctor("doctor-1");

        when(jdbc.queryForObject(
                        any(String.class),
                        eq(Boolean.class),
                        eq("dept-1"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR")))
                .thenReturn(true);
        when(jdbc.query(contains("select role_type"), any(RowMapper.class), eq("doctor-1")))
                .thenReturn(List.of("OUTPATIENT_DOCTOR"));
        when(jdbc.update(
                        contains("update staff"),
                        eq("Doctor"),
                        eq("Chief"),
                        eq("dept-1"),
                        eq("Cardiology"),
                        eq("doctor-1")))
                .thenReturn(1);
        doReturn(doctor).when(repository).findDoctor(any(String.class));

        assertThatThrownBy(() -> repository.createDoctor(
                        "0001", "Doctor", "Chief", "dept-1", "ADMIN", "Cardiology"))
                .isInstanceOf(IllegalArgumentException.class);

        DoctorCatalogRepository.Doctor created = repository.createDoctor(
                "0001", "Doctor", "Chief", "dept-1", "OUTPATIENT_DOCTOR", "Cardiology");
        DoctorCatalogRepository.Doctor updated =
                repository.updateDoctor("doctor-1", "Doctor", "Chief", "dept-1", "Cardiology");

        assertThat(created.id()).isEqualTo("doctor-1");
        assertThat(updated.id()).isEqualTo("doctor-1");
    }

    @Test
    void doctorEventsRoomsSchedulesAndStatsAreCalculatedFromJdbcResults() throws Exception {
        DoctorCatalogRepository repository = new DoctorCatalogRepository(jdbc);
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        LocalDate tomorrow = LocalDate.now().plusDays(8);

        when(resultSet.getString("id")).thenReturn("event-1", "event-1");
        when(resultSet.getString("staff_id")).thenReturn("doctor-1");
        when(resultSet.getString("doctor_name")).thenReturn("Doctor");
        when(resultSet.getString("department_name")).thenReturn("Internal");
        when(resultSet.getString("event_type")).thenReturn("LEAVE");
        when(resultSet.getString("note")).thenReturn("note");
        when(resultSet.getDate("event_date"))
                .thenReturn(java.sql.Date.valueOf(tomorrow), java.sql.Date.valueOf(tomorrow.plusDays(1)));
        when(resultSet.getString("period")).thenReturn(MORNING, AFTERNOON);
        doAnswer(invocation -> {
                    RowCallbackHandler handler = invocation.getArgument(1);
                    handler.processRow(resultSet);
                    handler.processRow(resultSet);
                    return null;
                })
                .when(jdbc)
                .query(contains("from doctor_event e"), any(RowCallbackHandler.class));
        when(jdbc.query(contains("from outpatient_doctor od"), any(RowMapper.class), eq("doctor-1")))
                .thenReturn(List.of(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")));
        when(jdbc.queryForObject(contains("select count(distinct s.staff_id)"), eq(Integer.class), eq(tomorrow)))
                .thenReturn(2);
        when(jdbc.queryForObject(contains("select count(distinct r.id)"), eq(Integer.class), eq(tomorrow)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("select count(distinct r.id)"), eq(Integer.class))).thenReturn(3);
        when(jdbc.query(contains("from schedule s"), any(RowMapper.class)))
                .thenReturn(List.of(schedule("schedule-1", "doctor-1", tomorrow, MORNING)));
        when(jdbc.query(
                        contains("from schedule_slot where schedule_id = ? order by start_time"),
                        any(RowMapper.class),
                        eq("schedule-1")))
                .thenReturn(List.of(new DoctorCatalogRepository.ScheduleTimeSlot(
                        "slot-1", "schedule-1", LocalTime.of(8, 0), 10)));

        assertThat(repository.doctorEvents()).hasSize(1);
        assertThat(repository.doctorEvents(tomorrow, tomorrow.plusDays(1))).hasSize(1);
        assertThat(repository.outpatientRoomForDoctor("doctor-1").id()).isEqualTo("room-1");
        assertThat(repository.doctorOperationsStats(tomorrow).roomCoverageRate()).isEqualTo(33);
        assertThat(repository.schedules(null, null)).isNotNull();
        assertThat(repository.schedules(null, null, tomorrow.minusDays(1), tomorrow.plusDays(1))).isNotNull();
        assertThat(repository.timeSlots("schedule-1")).hasSize(1);
    }

    @Test
    void createUpdateAndDeleteDoctorEventCoverLifecycle() throws Exception {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate eventDate = LocalDate.now().plusDays(8);
        AtomicReference<String> eventId = new AtomicReference<>("event-1");

        doReturn(doctor("doctor-1")).when(repository).findDoctor("doctor-1");
        when(jdbc.update(contains("insert into doctor_event"), any(), eq("doctor-1"), eq("LEAVE"), eq("note")))
                .thenAnswer(invocation -> {
                    eventId.set(invocation.getArgument(1, String.class));
                    return 1;
                });
        when(jdbc.update(contains("update doctor_event"), eq("doctor-1"), eq("LEAVE"), eq("note"), eq("event-1")))
                .thenReturn(1);
        when(jdbc.update("delete from doctor_event where id = ?", "event-1")).thenReturn(1);
        doAnswer(invocation -> List.of(new DoctorCatalogRepository.DoctorEvent(
                        eventId.get(), "doctor-1", "Doctor", "Internal", "LEAVE", List.of(eventDate), List.of(MORNING), "note")))
                .when(repository)
                .doctorEvents();

        assertThat(repository.createDoctorEvent(
                        "doctor-1", "LEAVE", List.of(eventDate), List.of(MORNING), "note"))
                .extracting(DoctorCatalogRepository.DoctorEvent::doctorId)
                .isEqualTo("doctor-1");
        eventId.set("event-1");
        assertThat(repository.updateDoctorEvent(
                        "event-1", "doctor-1", "LEAVE", List.of(eventDate.plusDays(1)), List.of(AFTERNOON), "note"))
                .extracting(DoctorCatalogRepository.DoctorEvent::doctorId)
                .isEqualTo("doctor-1");
        repository.deleteDoctorEvent("event-1");

        verify(jdbc).update("delete from doctor_event where id = ?", "event-1");
    }

    @Test
    void scheduleOperationsAndHelpersCoverFailureBranches() throws Exception {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate workDate = LocalDate.now().plusDays(8);

        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq("dept-1"))).thenReturn(true);
        when(jdbc.query(
                        contains("select id from schedule"),
                        any(RowMapper.class),
                        eq("dept-1"),
                        eq(workDate.minusDays(1)),
                        eq(workDate)))
                .thenReturn(List.of());
        when(jdbc.update(
                        "update schedule set status='SUSPENDED', suspension_reason=? where id=? and status='PUBLISHED'",
                        "leave",
                        "missing"))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.suspendSchedule("missing", "leave"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.createSchedule("doctor-1", "dept-1", workDate, "EVENING", 20))
                .isInstanceOf(IllegalArgumentException.class);

        repository.deleteSchedulesForDepartmentWindow("dept-1", workDate.minusDays(1), workDate);

        Method uniqueDates = DoctorCatalogRepository.class.getDeclaredMethod("uniqueDates", List.class);
        uniqueDates.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<LocalDate> dates = (List<LocalDate>) uniqueDates.invoke(
                repository,
                java.util.Arrays.asList(workDate.plusDays(1), null, workDate.plusDays(1), workDate));
        assertThat(dates).containsExactly(workDate, workDate.plusDays(1));

        Method uniquePeriods = DoctorCatalogRepository.class.getDeclaredMethod("uniquePeriods", List.class);
        uniquePeriods.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> periods = (List<String>) uniquePeriods.invoke(
                repository,
                List.of(MORNING, AFTERNOON, MORNING, "EVENING"));
        assertThat(periods).containsExactly(MORNING, AFTERNOON);
    }

    @Test
    void createDepartmentAndScheduleSuccessPathsGenerateExpectedJdbcCalls() {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate workDate = LocalDate.now().plusDays(8);
        DoctorCatalogRepository.Schedule created =
                schedule("schedule-created", "doctor-1", workDate, MORNING);

        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq("dept-1"))).thenReturn(true);
        when(jdbc.queryForObject(contains("select count(*) from schedule"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(
                        contains("from outpatient_doctor od"),
                        any(RowMapper.class),
                        eq("doctor-1")))
                .thenReturn(List.of(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")));
        doReturn(created).when(repository).findSchedule(any(String.class));

        DoctorCatalogRepository.Department department = repository.createDepartment("Internal", "desc");
        DoctorCatalogRepository.Schedule schedule =
                repository.createSchedule("doctor-1", "dept-1", workDate, MORNING, 20);

        assertThat(department.name()).isEqualTo("Internal");
        assertThat(schedule.id()).isEqualTo("schedule-created");
        verify(jdbc).update("insert into department (id,name,description) values (?,?,?)", department.id(), "Internal", "desc");
        verify(jdbc, times(8)).update(
                contains("insert into schedule_slot"),
                any(String.class),
                any(String.class),
                any(LocalTime.class),
                any(Integer.class));
    }

    @Test
    void rescheduleSuccessRebuildsDefaultAfternoonSlotsAndListTimeSlotsHandlesEmptyInput() {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate workDate = LocalDate.now().plusDays(8);
        DoctorCatalogRepository.Schedule current =
                schedule("schedule-1", "doctor-1", workDate, MORNING);
        DoctorCatalogRepository.Schedule moved =
                schedule("schedule-1", "doctor-1", workDate.plusDays(1), AFTERNOON);

        when(jdbc.queryForObject(contains("select count(*) from schedule"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(jdbc.query(
                        contains("from outpatient_doctor od"),
                        any(RowMapper.class),
                        eq("doctor-1")))
                .thenReturn(List.of(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")));
        when(jdbc.update(
                        "update schedule set work_date=?, period=? where id=? and status='PUBLISHED'",
                        workDate.plusDays(1),
                        AFTERNOON,
                        "schedule-1"))
                .thenReturn(1);
        doReturn(current, moved).when(repository).findSchedule("schedule-1");

        DoctorCatalogRepository.Schedule result =
                repository.reschedule("schedule-1", workDate.plusDays(1), AFTERNOON);

        assertThat(result.workDate()).isEqualTo(workDate.plusDays(1));
        assertThat(repository.timeSlots(List.of())).isEmpty();
        verify(jdbc).update("delete from schedule_slot where schedule_id = ?", "schedule-1");
        verify(jdbc, times(6)).update(
                contains("insert into schedule_slot"),
                any(String.class),
                any(String.class),
                any(LocalTime.class),
                any(Integer.class));
    }

    private DoctorCatalogRepository.Doctor doctor(String id) {
        return new DoctorCatalogRepository.Doctor(
                id,
                "0001",
                "Doctor",
                "Chief",
                "dept-1",
                "Internal",
                "Cardiology",
                "OUTPATIENT_DOCTOR",
                "room-1",
                "Room 1");
    }

    private DoctorCatalogRepository.Schedule schedule(String id, String doctorId, LocalDate date, String period) {
        return new DoctorCatalogRepository.Schedule(
                id, doctorId, "Doctor", "dept-1", "Internal", date, period, 20, "PUBLISHED", "room-1", "Room 1");
    }
}
