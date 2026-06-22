package com.cloudbrain.medicalrecord.repository;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.entity.MedicalRecordStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalRecordRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalRecord> rowMapper = new MedicalRecordRowMapper();

    public MedicalRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MedicalRecord> findAll() {
        return jdbcTemplate.query("select * from medical_record order by created_at desc", rowMapper);
    }

    public Optional<MedicalRecord> findById(String id) {
        List<MedicalRecord> result = jdbcTemplate.query("select * from medical_record where id = ?", rowMapper, id);
        return result.stream().findFirst();
    }

    public Optional<MedicalRecord> findByAppointmentId(String appointmentId) {
        List<MedicalRecord> result = jdbcTemplate.query(
                "select * from medical_record where appointment_id = ?",
                rowMapper,
                appointmentId);
        return result.stream().findFirst();
    }

    public MedicalRecord save(MedicalRecord record) {
        jdbcTemplate.update("""
                insert into medical_record (
                    id, appointment_id, patient_id, patient_name, doctor_id, doctor_name, department_name,
                    visit_date, period, ai_triage_summary, ai_risk_level, chief_complaint, present_illness,
                    diagnosis, treatment_plan, doctor_revision_note, status, updated_at, archived_at
                    , diagnosis_created_by_type, diagnosis_ai_record_id, diagnosis_confirmed_by, diagnosis_confirmed_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (appointment_id) do update set
                    chief_complaint = excluded.chief_complaint,
                    present_illness = excluded.present_illness,
                    diagnosis = excluded.diagnosis,
                    treatment_plan = excluded.treatment_plan,
                    doctor_revision_note = excluded.doctor_revision_note,
                    status = excluded.status,
                    updated_at = excluded.updated_at,
                    archived_at = excluded.archived_at
                    , diagnosis_created_by_type = excluded.diagnosis_created_by_type
                    , diagnosis_ai_record_id = excluded.diagnosis_ai_record_id
                    , diagnosis_confirmed_by = excluded.diagnosis_confirmed_by
                    , diagnosis_confirmed_at = excluded.diagnosis_confirmed_at
                """,
                record.getId(),
                record.getAppointmentId(),
                record.getPatientId(),
                record.getPatientName(),
                record.getDoctorId(),
                record.getDoctorName(),
                record.getDepartmentName(),
                record.getVisitDate(),
                record.getPeriod(),
                record.getAiTriageSummary(),
                record.getAiRiskLevel(),
                record.getChiefComplaint(),
                record.getPresentIllness(),
                record.getDiagnosis(),
                record.getTreatmentPlan(),
                record.getDoctorRevisionNote(),
                record.getStatus().name(),
                record.getUpdatedAt(),
                record.getArchivedAt(),
                record.getDiagnosisCreatedByType(),
                record.getDiagnosisAiRecordId(),
                record.getDiagnosisConfirmedBy(),
                record.getDiagnosisConfirmedAt());
        return record;
    }

    public int size() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from medical_record", Integer.class);
        return count == null ? 0 : count;
    }

    private static class MedicalRecordRowMapper implements RowMapper<MedicalRecord> {
        @Override
        public MedicalRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            MedicalRecord record = new MedicalRecord(
                    rs.getString("id"),
                    rs.getString("appointment_id"),
                    rs.getString("patient_id"),
                    rs.getString("patient_name"),
                    rs.getString("doctor_id"),
                    rs.getString("doctor_name"),
                    rs.getString("department_name"),
                    rs.getString("visit_date"),
                    rs.getString("period"),
                    rs.getString("ai_triage_summary"),
                    rs.getString("ai_risk_level"));
            record.restoreDoctorContent(
                    rs.getString("chief_complaint"),
                    rs.getString("present_illness"),
                    rs.getString("diagnosis"),
                    rs.getString("treatment_plan"),
                    rs.getString("doctor_revision_note"),
                    MedicalRecordStatus.valueOf(rs.getString("status")),
                    rs.getString("diagnosis_created_by_type"),
                    rs.getString("diagnosis_ai_record_id"),
                    rs.getString("diagnosis_confirmed_by"),
                    rs.getTimestamp("diagnosis_confirmed_at") == null ? null : rs.getTimestamp("diagnosis_confirmed_at").toLocalDateTime());
            return record;
        }
    }
}
