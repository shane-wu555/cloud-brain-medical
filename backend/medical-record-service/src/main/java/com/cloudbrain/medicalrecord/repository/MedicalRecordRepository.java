package com.cloudbrain.medicalrecord.repository;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.entity.MedicalRecordStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

    public List<MedicalRecord> findAllExcludingCancelledAppointments() {
        return jdbcTemplate.query("""
                select mr.* from medical_record mr
                where not exists (
                    select 1 from appointment.appointment a
                    where a.id = mr.appointment_id and a.status = 'CANCELLED'
                )
                order by mr.created_at desc
                """, rowMapper);
    }

    public Optional<MedicalRecord> findById(String id) {
        List<MedicalRecord> result = jdbcTemplate.query("select * from medical_record where id = ?", rowMapper, id);
        return result.stream().findFirst();
    }

    public Optional<MedicalRecord> findByAppointmentId(String appointmentId) {
        List<MedicalRecord> result = jdbcTemplate.query(
                "select * from medical_record where appointment_id = ?::uuid",
                rowMapper,
                appointmentId);
        return result.stream().findFirst();
    }

    public MedicalRecord save(MedicalRecord record,long expectedVersion) {
        int updated=jdbcTemplate.update("""
                update medical_record set chief_complaint=?,present_illness=?,past_history=?,allergy_history=?,
                    physical_examination=?,preliminary_diagnosis=?,diagnosis=?,treatment_plan=?,doctor_revision_note=?,
                    status=?,updated_at=?,archived_at=?,diagnosis_created_by_type=?,diagnosis_ai_record_id=?,
                    diagnosis_confirmed_by=?,diagnosis_confirmed_at=?,version=?
                where id=? and version=?
                """,record.getChiefComplaint(),record.getPresentIllness(),record.getPastHistory(),record.getAllergyHistory(),
                record.getPhysicalExamination(),record.getPreliminaryDiagnosis(),record.getDiagnosis(),record.getTreatmentPlan(),
                record.getDoctorRevisionNote(),record.getStatus().name(),record.getUpdatedAt(),record.getArchivedAt(),
                record.getDiagnosisCreatedByType(),record.getDiagnosisAiRecordId(),record.getDiagnosisConfirmedBy(),
                record.getDiagnosisConfirmedAt(),record.getVersion(),record.getId(),expectedVersion);
        if(updated!=1) throw new org.springframework.dao.OptimisticLockingFailureException("病历已被其他窗口更新，请刷新后重试");
        jdbcTemplate.update("""
                insert into medical_record_version
                    (id,medical_record_id,version,chief_complaint,present_illness,past_history,allergy_history,
                     physical_examination,preliminary_diagnosis,treatment_plan,doctor_revision_note,
                     diagnosis_created_by_type,diagnosis_ai_record_id,confirmed_by)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,java.util.UUID.randomUUID(),record.getId(),record.getVersion(),record.getChiefComplaint(),
                record.getPresentIllness(),record.getPastHistory(),record.getAllergyHistory(),record.getPhysicalExamination(),
                record.getPreliminaryDiagnosis(),record.getTreatmentPlan(),record.getDoctorRevisionNote(),
                record.getDiagnosisCreatedByType(),record.getDiagnosisAiRecordId(),record.getDiagnosisConfirmedBy());
        return record;
    }

    public List<MedicalRecord> findByPatientId(String patientId) {
        return jdbcTemplate.query("select * from medical_record where patient_id=?::uuid order by visit_date desc,created_at desc",rowMapper,patientId);
    }

    public List<MedicalRecord> findByPatientIdExcludingCancelledAppointments(String patientId) {
        return jdbcTemplate.query("""
                select mr.* from medical_record mr
                where mr.patient_id = ?::uuid
                  and not exists (
                      select 1 from appointment.appointment a
                      where a.id = mr.appointment_id and a.status = 'CANCELLED'
                  )
                order by mr.visit_date desc, mr.created_at desc
                """, rowMapper, patientId);
    }

    public void recordAccess(String recordId,String patientId,String actorId,String actorRole,String scope,String reason) {
        jdbcTemplate.update("""
                insert into medical_record_access_log
                    (id,medical_record_id,patient_id,actor_id,actor_role,access_scope,reason)
                values (?,?,?,?,?,?,?)
                """,java.util.UUID.randomUUID(),recordId,patientId,actorId,actorRole,scope,reason);
    }

    public List<AccessLog> accessLogs(String patientId) {
        StringBuilder sql = new StringBuilder("""
                select id,medical_record_id,patient_id,actor_id,actor_role,access_scope,reason,accessed_at
                from medical_record_access_log
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?");
            args.add(patientId);
        }
        sql.append(" order by accessed_at desc limit 200");
        return jdbcTemplate.query(sql.toString(),(rs,row)->new AccessLog(rs.getObject(1,java.util.UUID.class),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toLocalDateTime()),args.toArray());
    }
    public void linkReport(String appointmentId,String orderId,String reportId,String type,String conclusion,String confirmer,java.time.LocalDateTime confirmedAt){MedicalRecord record=findByAppointmentId(appointmentId).orElseThrow(()->new IllegalArgumentException("就诊病历不存在"));jdbcTemplate.update("""
            insert into medical_record_report_link(id,medical_record_id,medical_order_id,report_id,report_type,conclusion,confirmed_by,confirmed_at)
            values(?,?,?,?,?,?,?,?) on conflict(medical_order_id,report_id) do nothing
            """,java.util.UUID.randomUUID(),record.getId(),orderId,reportId,type,conclusion,confirmer,confirmedAt);}

    public MedicalRecord createInitialIfAbsent(MedicalRecord record) {
        jdbcTemplate.update("""
                insert into medical_record (
                    id,appointment_id,patient_id,patient_name,doctor_id,doctor_name,department_name,
                    visit_date,period,ai_triage_summary,ai_risk_level,status,updated_at)
                values (?,?::uuid,?::uuid,?,?,?,?,?,?,?,?,'DRAFT',now())
                on conflict (appointment_id) do nothing
                """,record.getId(),record.getAppointmentId(),record.getPatientId(),record.getPatientName(),
                record.getDoctorId(),record.getDoctorName(),record.getDepartmentName(),record.getVisitDate(),
                record.getPeriod(),record.getAiTriageSummary(),record.getAiRiskLevel());
        return findByAppointmentId(record.getAppointmentId()).orElseThrow();
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
                    rs.getString("past_history"),
                    rs.getString("allergy_history"),
                    rs.getString("physical_examination"),
                    rs.getString("preliminary_diagnosis"),
                    rs.getString("diagnosis"),
                    rs.getString("treatment_plan"),
                    rs.getString("doctor_revision_note"),
                    MedicalRecordStatus.valueOf(rs.getString("status")),
                    rs.getString("diagnosis_created_by_type"),
                    rs.getString("diagnosis_ai_record_id"),
                    rs.getString("diagnosis_confirmed_by"),
                    rs.getTimestamp("diagnosis_confirmed_at") == null ? null : rs.getTimestamp("diagnosis_confirmed_at").toLocalDateTime(),
                    rs.getLong("version"));
            return record;
        }
    }

    public record AccessLog(java.util.UUID id,String medicalRecordId,String patientId,String actorId,String actorRole,
            String accessScope,String reason,java.time.LocalDateTime accessedAt) {}
}
