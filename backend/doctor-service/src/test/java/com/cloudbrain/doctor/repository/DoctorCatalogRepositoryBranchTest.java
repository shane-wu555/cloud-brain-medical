package com.cloudbrain.doctor.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DoctorCatalogRepositoryBranchTest {
    private static final String MORNING = "\u4e0a\u5348";
    private static final String AFTERNOON = "\u4e0b\u5348";

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void createDoctorCoversOutpatientAndNonOutpatientAssignments() {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        DoctorCatalogRepository.Doctor doctor = doctor("doctor-1", "OUTPATIENT_DOCTOR");
        DoctorCatalogRepository.Doctor checker = doctor("doctor-2", "CHECK_DOCTOR");

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
        when(jdbc.queryForObject(
                        any(String.class),
                        eq(Boolean.class),
                        eq("dept-imaging"),
                        eq("CHECK_DOCTOR"),
                        eq("CHECK_DOCTOR"),
                        eq("CHECK_DOCTOR"),
                        eq("CHECK_DOCTOR"),
                        eq("CHECK_DOCTOR")))
                .thenReturn(true);
        doReturn(doctor, checker).when(repository).findDoctor(any(String.class));
        when(jdbc.update(contains("insert into staff"), any(), eq("0001"), eq("Doctor"), eq("Chief"), eq("dept-1"), eq("OUTPATIENT_DOCTOR"), eq("Cardiology")))
                .thenReturn(1);
        when(jdbc.update(contains("insert into staff"), any(), eq("0002"), eq("Checker"), eq("Attending"), eq("dept-imaging"), eq("CHECK_DOCTOR"), eq("CT")))
                .thenReturn(1);

        assertThat(repository.createDoctor("0001", "Doctor", "Chief", "dept-1", "OUTPATIENT_DOCTOR", "Cardiology"))
                .isEqualTo(doctor);
        assertThat(repository.createDoctor("0002", "Checker", "Attending", "dept-imaging", "CHECK_DOCTOR", "CT"))
                .isEqualTo(checker);

        verify(jdbc).update(contains("insert into outpatient_room"), eq("room-dept-1"), eq("dept-1"));
        verify(jdbc).update(contains("insert into outpatient_doctor"), any(String.class), eq("room-dept-1"));
        verify(jdbc).update(eq("delete from outpatient_doctor where staff_id = ?"), any(String.class));
    }

    @Test
    void repositoryEdgeCasesCoverNullStatsAndMissingLookups() throws Exception {
        DoctorCatalogRepository repository = new DoctorCatalogRepository(jdbc);
        LocalDate workDate = LocalDate.now().plusDays(8);

        when(jdbc.query(contains("where s.id = ?"), any(RowMapper.class), eq("missing"))).thenReturn(List.of());
        when(jdbc.query(contains("where od.staff_id = ?"), any(RowMapper.class), eq("missing"))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("select count(distinct s.staff_id)"), eq(Integer.class), eq(workDate))).thenReturn(null);
        when(jdbc.queryForObject(contains("select count(distinct r.id)"), eq(Integer.class), eq(workDate))).thenReturn(null);
        when(jdbc.queryForObject(contains("select count(distinct r.id)"), eq(Integer.class))).thenReturn(null);
        when(jdbc.query(
                        contains("from outpatient_room r"),
                        any(RowMapper.class),
                        eq("dept-1")))
                .thenReturn(List.of(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")));
        when(jdbc.query(
                        contains("from schedule_slot where schedule_id in"),
                        any(RowMapper.class),
                        any()))
                .thenReturn(List.of(new DoctorCatalogRepository.ScheduleTimeSlot("slot-1", "schedule-1", LocalTime.of(8, 0), 10)));

        assertThatThrownBy(() -> repository.findDoctor("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.outpatientRoomForDoctor("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.doctorOperationsStats(workDate).roomCoverageRate()).isZero();
        assertThat(repository.outpatientRoomsWithDoctors("dept-1")).hasSize(1);
        assertThat(repository.timeSlots(List.of("schedule-1"))).hasSize(1);

        Method defaultStartTimes = DoctorCatalogRepository.class.getDeclaredMethod("defaultStartTimes", String.class);
        defaultStartTimes.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<LocalTime> morningSlots = (List<LocalTime>) defaultStartTimes.invoke(repository, MORNING);
        @SuppressWarnings("unchecked")
        List<LocalTime> afternoonSlots = (List<LocalTime>) defaultStartTimes.invoke(repository, AFTERNOON);
        @SuppressWarnings("unchecked")
        List<LocalTime> unknownSlots = (List<LocalTime>) defaultStartTimes.invoke(repository, "NIGHT");
        assertThat(morningSlots).hasSize(8);
        assertThat(afternoonSlots).hasSize(6);
        assertThat(unknownSlots).isEmpty();
    }

    @Test
    void deleteSchedulesAndEventValidationCoverRemainingBranches() {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate workDate = LocalDate.now().plusDays(8);

        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), eq("dept-1"))).thenReturn(false, true);
        assertThatThrownBy(() -> repository.deleteSchedulesForDepartmentWindow("dept-1", workDate, workDate.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        when(jdbc.query(
                        contains("select id from schedule"),
                        any(RowMapper.class),
                        eq("dept-1"),
                        eq(workDate),
                        eq(workDate.plusDays(1))))
                .thenReturn(List.of("schedule-1", "schedule-2"));
        repository.deleteSchedulesForDepartmentWindow("dept-1", workDate, workDate.plusDays(1));
        verify(jdbc).update("delete from schedule_slot where schedule_id in (?,?)", "schedule-1", "schedule-2");
        verify(jdbc).update("delete from schedule where id in (?,?)", "schedule-1", "schedule-2");

        doReturn(doctor("doctor-1", "OUTPATIENT_DOCTOR")).when(repository).findDoctor("doctor-1");
        assertThatThrownBy(() -> repository.createDoctorEvent("doctor-1", "OTHER", List.of(workDate), List.of(MORNING), "note"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.createDoctorEvent("doctor-1", "LEAVE", List.of(workDate.minusDays(3)), List.of(MORNING), "note"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.createDoctorEvent("doctor-1", "LEAVE", List.of(workDate), List.of("NIGHT"), "note"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conflictAndMissingLookupsCoverAdditionalRepositoryBranches() throws Exception {
        DoctorCatalogRepository repository = spy(new DoctorCatalogRepository(jdbc));
        LocalDate workDate = LocalDate.now().plusDays(8);
        DoctorCatalogRepository.Schedule current = new DoctorCatalogRepository.Schedule(
                "schedule-1", "doctor-1", "Doctor", "dept-1", "Internal", workDate, MORNING, 20, "PUBLISHED", "room-1", "Room 1");

        when(jdbc.queryForObject(
                        any(String.class),
                        eq(Boolean.class),
                        eq("dept-imaging"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR")))
                .thenReturn(false);
        when(jdbc.queryForObject(contains("select count(*) from schedule"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1, 0, 0, 0);
        doReturn(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1"))
                .when(repository)
                .outpatientRoomForDoctor("doctor-1");
        when(jdbc.queryForObject(contains("join outpatient_doctor od"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1, 0);
        when(jdbc.query(contains("where s.id = ?"), any(RowMapper.class), eq("missing"))).thenReturn(List.of());
        when(jdbc.query(contains("select role_type"), any(RowMapper.class), eq("doctor-1"))).thenReturn(List.of("OUTPATIENT_DOCTOR"));
        when(jdbc.queryForObject(
                        any(String.class),
                        eq(Boolean.class),
                        eq("dept-1"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR"),
                        eq("OUTPATIENT_DOCTOR")))
                .thenReturn(true, true, true);
        when(jdbc.update(
                        contains("update staff"),
                        eq("Doctor"),
                        eq("Chief"),
                        eq("dept-1"),
                        eq("Cardiology"),
                        eq("doctor-1")))
                .thenReturn(0);
        doReturn(current).when(repository).findSchedule("schedule-1");
        when(jdbc.update(
                        "update schedule set work_date=?, period=? where id=? and status='PUBLISHED'",
                        workDate.plusDays(1),
                        AFTERNOON,
                        "schedule-1"))
                .thenReturn(0);

        Method validateDoctorDepartment =
                DoctorCatalogRepository.class.getDeclaredMethod("validateDoctorDepartment", String.class, String.class);
        validateDoctorDepartment.setAccessible(true);
        assertThatThrownBy(() -> validateDoctorDepartment.invoke(repository, "OUTPATIENT_DOCTOR", "dept-imaging"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        Method validateDoctorCanScheduleOnDate = DoctorCatalogRepository.class.getDeclaredMethod(
                "validateDoctorCanScheduleOnDate", String.class, LocalDate.class, String.class, String.class);
        validateDoctorCanScheduleOnDate.setAccessible(true);
        assertThatThrownBy(() -> validateDoctorCanScheduleOnDate.invoke(repository, "doctor-1", workDate, MORNING, null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        Method validateRoomCanScheduleOnDate = DoctorCatalogRepository.class.getDeclaredMethod(
                "validateRoomCanScheduleOnDate", String.class, LocalDate.class, String.class, String.class);
        validateRoomCanScheduleOnDate.setAccessible(true);
        assertThatThrownBy(() -> validateRoomCanScheduleOnDate.invoke(repository, "doctor-1", workDate, MORNING, null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.findSchedule("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.updateDoctor("doctor-1", "Doctor", "Chief", "dept-1", "Cardiology"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.reschedule("schedule-1", workDate.plusDays(1), AFTERNOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DoctorCatalogRepository.Doctor doctor(String id, String roleType) {
        return new DoctorCatalogRepository.Doctor(
                id,
                "0001",
                "Doctor",
                "Chief",
                "dept-1",
                "Internal",
                "Cardiology",
                roleType,
                "room-1",
                "Room 1");
    }
}
