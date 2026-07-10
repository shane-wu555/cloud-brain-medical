package com.cloudbrain.appointment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AppointmentRepositoryTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void findAllAndFindByIdMapAppointmentsFromRows() throws Exception {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.query(eq("select * from appointment order by visit_date, queue_number"), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(mapAppointment(invocation.getArgument(1), values(
                        "id", "appt-1",
                        "slot_id", "slot-1",
                        "patient_id", "patient-1",
                        "patient_name", "Patient",
                        "doctor_id", "doctor-1",
                        "doctor_name", "Doctor",
                        "department_id", "dept-1",
                        "department_name", "Dept",
                        "visit_date", LocalDate.of(2026, 7, 9),
                        "period", "MORNING",
                        "start_time", LocalTime.of(8, 0),
                        "source", "ONLINE",
                        "status", "FINISHED",
                        "payment_status", "PAID",
                        "payment_method", "WECHAT",
                        "queue_number", 1,
                        "missed_count", 2,
                        "business_no", "AP20260709000001"))));
        when(jdbcTemplate.query(eq("select * from appointment where id = ?::uuid"), any(RowMapper.class), eq("appt-2")))
                .thenAnswer(invocation -> List.of(mapAppointment(invocation.getArgument(1), values(
                        "id", "appt-2",
                        "slot_id", "slot-2",
                        "patient_id", "patient-2",
                        "patient_name", "Patient 2",
                        "doctor_id", "doctor-2",
                        "doctor_name", "Doctor 2",
                        "department_id", "dept-2",
                        "department_name", "Dept 2",
                        "visit_date", LocalDate.of(2026, 7, 10),
                        "period", "AFTERNOON",
                        "start_time", LocalTime.of(14, 0),
                        "source", "OFFLINE",
                        "status", "UNKNOWN",
                        "payment_status", "UNPAID",
                        "payment_method", null,
                        "queue_number", 2,
                        "missed_count", 0,
                        "business_no", "AP20260710000002"))));

        List<Appointment> all = repository.findAll();
        Optional<Appointment> found = repository.findById("appt-2");

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(AppointmentStatus.FINISHED);
        assertThat(all.get(0).getPaymentMethod()).isEqualTo("WECHAT");
        assertThat(all.get(0).getMissedCount()).isEqualTo(2);
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(found.orElseThrow().getSource()).isEqualTo(AppointmentSource.OFFLINE);
    }

    @Test
    void findByIdForUpdateAndFindExpiredPendingIdsDelegateToJdbc() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        Appointment appointment = appointment("appt-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(jdbcTemplate.query(eq("select * from appointment where id = ?::uuid for update"), any(RowMapper.class), eq("appt-1")))
                .thenReturn(List.of(appointment));
        when(jdbcTemplate.query(contains("select id from appointment"), any(RowMapper.class))).thenReturn(List.of("appt-1", "appt-2"));

        assertThat(repository.findByIdForUpdate("appt-1")).contains(appointment);
        assertThat(repository.findExpiredPendingIds()).containsExactly("appt-1", "appt-2");
    }

    @Test
    void existsActiveAtStartTimeAndSizeHandleNulls() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("select count(*) from appointment"), eq(Integer.class),
                eq("patient-1"), eq("2026-07-09"), eq(LocalTime.of(8, 0)))).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("select count(*) from appointment"), eq(Integer.class))).thenReturn(null);

        assertThat(repository.existsActiveAtStartTime("patient-1", "2026-07-09", LocalTime.of(8, 0))).isTrue();
        assertThat(repository.size()).isZero();
    }

    @Test
    void existsActiveAtStartTimeAndSizeCoverFalseBranches() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("select count(*) from appointment"), eq(Integer.class),
                eq("patient-1"), eq("2026-07-09"), eq(LocalTime.of(8, 0)))).thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("select count(*) from appointment"), eq(Integer.class),
                eq("patient-2"), eq("2026-07-09"), eq(LocalTime.of(9, 0)))).thenReturn(null);
        when(jdbcTemplate.queryForObject(eq("select count(*) from appointment"), eq(Integer.class))).thenReturn(8);

        assertThat(repository.existsActiveAtStartTime("patient-1", "2026-07-09", LocalTime.of(8, 0))).isFalse();
        assertThat(repository.existsActiveAtStartTime("patient-2", "2026-07-09", LocalTime.of(9, 0))).isFalse();
        assertThat(repository.size()).isEqualTo(8);
    }

    @Test
    void saveGeneratesBusinessNumberAndReloadsPersistedBusinessNo() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        Appointment appointment = appointment("appt-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(jdbcTemplate.queryForObject(eq("select nextval('appt_business_no_seq')"), eq(Long.class))).thenReturn(12L);
        when(jdbcTemplate.update(contains("insert into appointment"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("select business_no from appointment where id=?::uuid"), eq(String.class), eq("appt-1")))
                .thenReturn("AP20260710000012");

        Appointment saved = repository.save(appointment);

        assertThat(saved.getBusinessNo()).isEqualTo("AP20260710000012");
    }

    @Test
    void saveKeepsExistingBusinessNumberWhenPresent() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        Appointment appointment = appointment("appt-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        appointment.restoreBusinessNo("EXISTING-NO");
        when(jdbcTemplate.update(contains("insert into appointment"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("select business_no from appointment where id=?::uuid"), eq(String.class), eq("appt-1")))
                .thenReturn("EXISTING-NO");

        Appointment saved = repository.save(appointment);

        assertThat(saved.getBusinessNo()).isEqualTo("EXISTING-NO");
    }

    @Test
    void saveUsesZeroWhenSequenceMissing() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        Appointment appointment = appointment("appt-2", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(jdbcTemplate.queryForObject(eq("select nextval('appt_business_no_seq')"), eq(Long.class))).thenReturn(null);
        when(jdbcTemplate.update(contains("insert into appointment"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("select business_no from appointment where id=?::uuid"), eq(String.class), eq("appt-2")))
                .thenReturn("AP20260710000000");

        Appointment saved = repository.save(appointment);

        assertThat(saved.getBusinessNo()).isEqualTo("AP20260710000000");
    }

    @Test
    void dashboardStatsMapsCountsAndDepartmentLoads() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        LocalDate visitDate = LocalDate.of(2026, 7, 9);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(visitDate))).thenReturn(12, 5, 4);
        when(jdbcTemplate.query(contains("group by department_name"), any(RowMapper.class), eq(visitDate)))
                .thenReturn(List.of(new AppointmentRepository.DepartmentLoad("Cardiology", 7)));

        AppointmentRepository.DashboardAppointmentStats stats = repository.dashboardStats(visitDate);

        assertThat(stats.todayAppointments()).isEqualTo(12);
        assertThat(stats.waitingVisits()).isEqualTo(5);
        assertThat(stats.aiTriageCount()).isEqualTo(4);
        assertThat(stats.departmentLoads()).containsExactly(new AppointmentRepository.DepartmentLoad("Cardiology", 7));
    }

    @Test
    void dashboardStatsDefaultsNullCountsToZero() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        LocalDate visitDate = LocalDate.of(2026, 7, 9);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(visitDate))).thenReturn(null, null, null);
        when(jdbcTemplate.query(contains("group by department_name"), any(RowMapper.class), eq(visitDate)))
                .thenReturn(List.of());

        AppointmentRepository.DashboardAppointmentStats stats = repository.dashboardStats(visitDate);

        assertThat(stats.todayAppointments()).isZero();
        assertThat(stats.waitingVisits()).isZero();
        assertThat(stats.aiTriageCount()).isZero();
        assertThat(stats.departmentLoads()).isEmpty();
    }

    @Test
    void schedulingHistorySummaryClampsLookbackDaysAndMarksTrainingReady() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(365))).thenReturn(220);
        when(jdbcTemplate.query(contains("limit 100"), any(RowMapper.class), eq(365)))
                .thenReturn(List.of(new AppointmentRepository.DoctorVisitAverage("doctor-1", "Doctor", "dept-1", "Dept", 15)));
        when(jdbcTemplate.query(contains("group by department_id"), any(RowMapper.class), eq(365)))
                .thenReturn(List.of(new AppointmentRepository.DepartmentVisitAverage("dept-1", "Dept", 30)));
        when(jdbcTemplate.query(contains("group by dow"), any(RowMapper.class), eq(365)))
                .thenReturn(List.of(new AppointmentRepository.WeekdayVisitAverage(1, 40)));

        AppointmentRepository.SchedulingHistorySummary summary = repository.schedulingHistorySummary(500);

        assertThat(summary.lookbackDays()).isEqualTo(365);
        assertThat(summary.sampleSize()).isEqualTo(220);
        assertThat(summary.trainingReady()).isTrue();
        assertThat(summary.doctorAverages()).hasSize(1);
        assertThat(summary.departmentAverages()).hasSize(1);
        assertThat(summary.weekdayAverages()).hasSize(1);
    }

    @Test
    void schedulingHistorySummaryClampsLowerBoundAndTrainingFlagFalse() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(7))).thenReturn(199);
        when(jdbcTemplate.query(contains("limit 100"), any(RowMapper.class), eq(7))).thenReturn(List.of());
        when(jdbcTemplate.query(contains("group by department_id"), any(RowMapper.class), eq(7))).thenReturn(List.of());
        when(jdbcTemplate.query(contains("group by dow"), any(RowMapper.class), eq(7))).thenReturn(List.of());

        AppointmentRepository.SchedulingHistorySummary summary = repository.schedulingHistorySummary(1);

        assertThat(summary.lookbackDays()).isEqualTo(7);
        assertThat(summary.trainingReady()).isFalse();
    }

    @Test
    void nextQueueNumberUsesAdvisoryLockAndDefaultsFromNullMax() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), eq("doctor-1:2026-07-09")))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(contains("select coalesce(max(queue_number), 0)"), eq(Integer.class),
                eq("doctor-1"), eq("2026-07-09"))).thenReturn(null);

        assertThat(repository.nextQueueNumber("doctor-1", "2026-07-09")).isEqualTo(1);
    }

    @Test
    void nextQueueNumberIncrementsExistingMaximum() {
        AppointmentRepository repository = new AppointmentRepository(jdbcTemplate);
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), eq("doctor-1:2026-07-09")))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(contains("select coalesce(max(queue_number), 0)"), eq(Integer.class),
                eq("doctor-1"), eq("2026-07-09"))).thenReturn(8);

        assertThat(repository.nextQueueNumber("doctor-1", "2026-07-09")).isEqualTo(9);
    }

    @Test
    void insertForRevisitThrowsWhenNoServingPositionExists() {
        AppointmentRepository repository = spy(new AppointmentRepository(jdbcTemplate));
        Appointment appointment = appointment("appt-1", AppointmentStatus.FINISHED, PaymentStatus.PAID);
        doReturn(Optional.of(appointment)).when(repository).findById("appt-1");
        doReturn(Optional.of(appointment)).when(repository).findByIdForUpdate("appt-1");
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), anyString()))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(contains("select coalesce"), eq(Integer.class),
                eq("doctor-1"), eq(appointment.getVisitDate()), eq("doctor-1"), eq(appointment.getVisitDate())))
                .thenReturn(null);

        assertThatThrownBy(() -> repository.insertForRevisit("appt-1", 3)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertForRevisitPushesQueueAndSavesUpdatedAppointment() {
        AppointmentRepository repository = spy(new AppointmentRepository(jdbcTemplate));
        Appointment current = appointment("appt-1", AppointmentStatus.FINISHED, PaymentStatus.PAID);
        doReturn(Optional.of(current)).when(repository).findById("appt-1");
        doReturn(Optional.of(current)).when(repository).findByIdForUpdate("appt-1");
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).save(any(Appointment.class));
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), anyString()))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(contains("select coalesce"), eq(Integer.class),
                eq("doctor-1"), eq(current.getVisitDate()), eq("doctor-1"), eq(current.getVisitDate())))
                .thenReturn(10);

        Appointment revisit = repository.insertForRevisit("appt-1", 3);

        assertThat(revisit.getStatus()).isEqualTo(AppointmentStatus.REVISIT_WAITING);
        assertThat(revisit.getQueueNumber()).isEqualTo(13);
    }

    @Test
    void moveToTailSetsQueueNumberToTailAndIncrementsMissedCount() {
        AppointmentRepository repository = spy(new AppointmentRepository(jdbcTemplate));
        Appointment current = appointment("appt-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        Appointment updated = appointment("appt-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        updated.restorePersistenceState(null, 1);
        doReturn(Optional.of(current), Optional.of(updated)).when(repository).findById("appt-1");
        doReturn(Optional.of(current)).when(repository).findByIdForUpdate("appt-1");
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), anyString()))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(
                eq("select coalesce(max(queue_number), 0) + 1 from appointment where doctor_id = ? and visit_date = ?::date"),
                eq(Integer.class),
                eq("doctor-1"),
                eq(current.getVisitDate()))).thenReturn(99);

        Appointment skipped = repository.moveToTail("appt-1");

        assertThat(skipped.getMissedCount()).isEqualTo(1);
        verify(jdbcTemplate).update(
                eq("update appointment set queue_number = ?, missed_count = missed_count + 1, status = 'WAITING' where id = ?::uuid"),
                eq(99), eq("appt-1"));
    }

    @Test
    void moveToTailRejectsNonWaitingStatus() {
        AppointmentRepository repository = spy(new AppointmentRepository(jdbcTemplate));
        Appointment current = appointment("appt-1", AppointmentStatus.CANCELLED, PaymentStatus.CANCELLED);
        doReturn(Optional.of(current), Optional.of(current)).when(repository).findById("appt-1");
        doReturn(Optional.of(current)).when(repository).findByIdForUpdate("appt-1");
        when(jdbcTemplate.query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(ResultSetExtractor.class), anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> repository.moveToTail("appt-1")).isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    private Appointment mapAppointment(Object mapperObject, Map<String, Object> values) throws SQLException {
        RowMapper<Appointment> mapper = (RowMapper<Appointment>) mapperObject;
        ResultSet resultSet = resultSet(values);
        return mapper.mapRow(resultSet, 0);
    }

    private ResultSet resultSet(Map<String, Object> values) throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0, String.class));
            return value == null ? null : String.valueOf(value);
        });
        when(resultSet.getDate("visit_date")).thenReturn(Date.valueOf((LocalDate) values.get("visit_date")));
        when(resultSet.getObject(eq("start_time"), eq(LocalTime.class))).thenReturn((LocalTime) values.get("start_time"));
        when(resultSet.getInt("queue_number")).thenReturn((Integer) values.get("queue_number"));
        when(resultSet.getInt("missed_count")).thenReturn((Integer) values.get("missed_count"));
        return resultSet;
    }

    private Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }

    private Appointment appointment(String id, AppointmentStatus status, PaymentStatus paymentStatus) {
        return new Appointment(
                id,
                "slot-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.of(2026, 7, 10),
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                status,
                paymentStatus,
                null,
                "LOW",
                null,
                1);
    }
}
