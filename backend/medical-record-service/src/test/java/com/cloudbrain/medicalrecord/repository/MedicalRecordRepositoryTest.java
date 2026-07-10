package com.cloudbrain.medicalrecord.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.entity.MedicalRecordStatus;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class MedicalRecordRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void findAndCreateInitialUseAppointmentQuery() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecord record = record("appointment-1");
        when(jdbcTemplate.query(eq("select * from medical_record order by created_at desc"), any(RowMapper.class)))
                .thenReturn(List.of(record));
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("where not exists"), any(RowMapper.class)))
                .thenReturn(List.of(record));
        when(jdbcTemplate.query(eq("select * from medical_record where id = ?"), any(RowMapper.class), eq("record-appointment-1")))
                .thenReturn(List.of(record));
        when(jdbcTemplate.query(eq("select * from medical_record where appointment_id = ?::uuid"), any(RowMapper.class), eq("appointment-1")))
                .thenReturn(List.of(record));

        assertThat(repository.findAll()).containsExactly(record);
        assertThat(repository.findAllExcludingCancelledAppointments()).containsExactly(record);
        assertThat(repository.findById("record-appointment-1")).contains(record);
        assertThat(repository.findByAppointmentId("appointment-1")).contains(record);
        assertThat(repository.createInitialIfAbsent(record)).isEqualTo(record);
    }

    @Test
    void saveWritesSnapshotOrThrowsOptimisticLockingFailure() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecord record = record("appointment-2");
        record.writeDoctorNote("chief", "present", "past", "allergy", "exam", "diagnosis", "plan", "", "HUMAN", null);
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("where id = ? and version = ?"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        assertThat(repository.save(record, 0)).isEqualTo(record);
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("insert into medical_record_version"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());

        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("where id = ? and version = ?"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        assertThatThrownBy(() -> repository.save(record, 999)).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void saveUsesDoctorIdWhenDiagnosisConfirmerMissing() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecord record = record("appointment-2b");
        record.writeDoctorNote("chief", "present", "past", "allergy", "exam", "diagnosis", "plan", "note", "AI", "ai-2");
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("where id = ? and version = ?"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        repository.save(record, 0);

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("insert into medical_record_version"), eq(record.getId()),
                eq(record.getVersion()), eq("chief"), eq("present"), eq("past"), eq("allergy"), eq("exam"), eq("diagnosis"),
                eq("plan"), eq("note"), eq("AI"), eq("ai-2"), eq("doctor-1"));
    }

    @Test
    void accessLogsAndSizeHandleOptionalFilterAndNullCount() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecordRepository.AccessLog accessLog =
                new MedicalRecordRepository.AccessLog(1L, "record-1", "patient-1", "doctor-1", "OUTPATIENT_DOCTOR", "LIST", "reason",
                        LocalDateTime.parse("2026-07-10T10:00:00"));
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("and patient_id = ?::uuid"), any(RowMapper.class), eq("patient-1")))
                .thenReturn(List.of(accessLog));
        when(jdbcTemplate.query(eq("""
                select id, record_id, patient_id, actor_id, actor_role, scope, reason, accessed_at
                from medical_record_access_log where 1 = 1
                 order by accessed_at desc limit 200"""),
                any(RowMapper.class), new Object[0]))
                .thenReturn(List.of(accessLog));
        when(jdbcTemplate.queryForObject("select count(*) from medical_record", Integer.class)).thenReturn(null);

        assertThat(repository.accessLogs("patient-1")).containsExactly(accessLog);
        assertThat(repository.accessLogs(null)).containsExactly(accessLog);
        assertThat(repository.size()).isZero();
    }

    @Test
    void findByPatientIdRecordAccessAndSizeUseExpectedQueries() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecord record = record("appointment-4");
        when(jdbcTemplate.query(
                eq("select * from medical_record where patient_id = ?::uuid order by visit_date desc, created_at desc"),
                any(RowMapper.class), eq("patient-1")))
                .thenReturn(List.of(record));
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("where mr.patient_id = ?::uuid"),
                any(RowMapper.class), eq("patient-1")))
                .thenReturn(List.of(record));
        when(jdbcTemplate.queryForObject("select count(*) from medical_record", Integer.class)).thenReturn(3);

        assertThat(repository.findByPatientId("patient-1")).containsExactly(record);
        assertThat(repository.findByPatientIdExcludingCancelledAppointments("patient-1")).containsExactly(record);

        repository.recordAccess("record-1", "patient-1", "doctor-1", "OUTPATIENT_DOCTOR", "DETAIL", "follow-up");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("insert into medical_record_access_log"),
                eq("record-1"), eq("patient-1"), eq("doctor-1"), eq("OUTPATIENT_DOCTOR"), eq("DETAIL"), eq("follow-up"));
        assertThat(repository.size()).isEqualTo(3);
    }

    @Test
    void linkReportLoadsRecordOrThrows() {
        MedicalRecordRepository repository = new MedicalRecordRepository(jdbcTemplate);
        MedicalRecord record = record("appointment-3");
        when(jdbcTemplate.query(eq("select * from medical_record where appointment_id = ?::uuid"), any(RowMapper.class), eq("appointment-3")))
                .thenReturn(List.of(record));
        when(jdbcTemplate.query(eq("select * from medical_record where appointment_id = ?::uuid"), any(RowMapper.class), eq("missing")))
                .thenReturn(List.of());

        repository.linkReport("appointment-3", "order-1", "report-1", "LAB", "ok", "doctor", LocalDateTime.parse("2026-07-10T10:30:00"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("insert into medical_record_report_link"), eq(record.getId()), eq("order-1"),
                eq("report-1"), eq("LAB"), eq("ok"), eq("doctor"), any());

        assertThatThrownBy(() -> repository.linkReport("missing", "order-1", "report-1", "LAB", "ok", "doctor", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rowMapperRestoresDoctorContent() throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("record-1");
        when(resultSet.getString("appointment_id")).thenReturn("appointment-1");
        when(resultSet.getString("patient_id")).thenReturn("patient-1");
        when(resultSet.getString("patient_name")).thenReturn("Alice");
        when(resultSet.getString("doctor_id")).thenReturn("doctor-1");
        when(resultSet.getString("doctor_name")).thenReturn("Doctor");
        when(resultSet.getString("department_name")).thenReturn("Dept");
        when(resultSet.getDate("visit_date")).thenReturn(java.sql.Date.valueOf("2026-07-10"));
        when(resultSet.getString("ai_triage_summary")).thenReturn("summary");
        when(resultSet.getString("chief_complaint")).thenReturn("chief");
        when(resultSet.getString("present_illness")).thenReturn("present");
        when(resultSet.getString("past_history")).thenReturn("past");
        when(resultSet.getString("allergy_history")).thenReturn("allergy");
        when(resultSet.getString("physical_examination")).thenReturn("exam");
        when(resultSet.getString("preliminary_diagnosis")).thenReturn("diagnosis");
        when(resultSet.getString("diagnosis")).thenReturn("diagnosis");
        when(resultSet.getString("treatment_plan")).thenReturn("plan");
        when(resultSet.getString("doctor_revision_note")).thenReturn("edited");
        when(resultSet.getString("status")).thenReturn(MedicalRecordStatus.ACTIVE.name());
        when(resultSet.getString("diagnosis_created_by_type")).thenReturn("AI");
        when(resultSet.getString("diagnosis_ai_record_id")).thenReturn("ai-1");
        when(resultSet.getString("diagnosis_confirmed_by")).thenReturn("doctor-1");
        when(resultSet.getTimestamp("diagnosis_confirmed_at")).thenReturn(Timestamp.valueOf("2026-07-10 10:00:00"));
        when(resultSet.getLong("version")).thenReturn(3L);

        java.lang.reflect.Field field = MedicalRecordRepository.class.getDeclaredField("rowMapper");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<MedicalRecord> rowMapper = (RowMapper<MedicalRecord>) field.get(new MedicalRecordRepository(jdbcTemplate));

        MedicalRecord mapped = rowMapper.mapRow(resultSet, 0);

        assertThat(mapped.getStatus()).isEqualTo(MedicalRecordStatus.ACTIVE);
        assertThat(mapped.getDiagnosisCreatedByType()).isEqualTo("AI");
        assertThat(mapped.getVersion()).isEqualTo(3L);
    }

    @Test
    void rowMapperHandlesNullableVisitDateAndDiagnosisConfirmation() throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("record-2");
        when(resultSet.getString("appointment_id")).thenReturn("appointment-2");
        when(resultSet.getString("patient_id")).thenReturn("patient-2");
        when(resultSet.getString("patient_name")).thenReturn("Bob");
        when(resultSet.getString("doctor_id")).thenReturn("doctor-2");
        when(resultSet.getString("doctor_name")).thenReturn("Doctor Two");
        when(resultSet.getString("department_name")).thenReturn("Dept");
        when(resultSet.getDate("visit_date")).thenReturn(null);
        when(resultSet.getString("ai_triage_summary")).thenReturn(null);
        when(resultSet.getString("chief_complaint")).thenReturn(null);
        when(resultSet.getString("present_illness")).thenReturn(null);
        when(resultSet.getString("past_history")).thenReturn(null);
        when(resultSet.getString("allergy_history")).thenReturn(null);
        when(resultSet.getString("physical_examination")).thenReturn(null);
        when(resultSet.getString("preliminary_diagnosis")).thenReturn(null);
        when(resultSet.getString("diagnosis")).thenReturn(null);
        when(resultSet.getString("treatment_plan")).thenReturn(null);
        when(resultSet.getString("doctor_revision_note")).thenReturn(null);
        when(resultSet.getString("status")).thenReturn(MedicalRecordStatus.DRAFT.name());
        when(resultSet.getString("diagnosis_created_by_type")).thenReturn(null);
        when(resultSet.getString("diagnosis_ai_record_id")).thenReturn(null);
        when(resultSet.getString("diagnosis_confirmed_by")).thenReturn(null);
        when(resultSet.getTimestamp("diagnosis_confirmed_at")).thenReturn(null);
        when(resultSet.getLong("version")).thenReturn(0L);

        java.lang.reflect.Field field = MedicalRecordRepository.class.getDeclaredField("rowMapper");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<MedicalRecord> rowMapper = (RowMapper<MedicalRecord>) field.get(new MedicalRecordRepository(jdbcTemplate));

        MedicalRecord mapped = rowMapper.mapRow(resultSet, 0);

        assertThat(mapped.getVisitDate()).isNull();
        assertThat(mapped.getStatus()).isEqualTo(MedicalRecordStatus.DRAFT);
        assertThat(mapped.getDiagnosisConfirmedAt()).isNull();
        assertThat(mapped.getVersion()).isZero();
    }

    private MedicalRecord record(String appointmentId) {
        return new MedicalRecord(
                "record-" + appointmentId,
                appointmentId,
                "patient-1",
                "Alice",
                "doctor-1",
                "Doctor",
                "Dept",
                "2026-07-10",
                "AM",
                "summary",
                "LOW");
    }
}
