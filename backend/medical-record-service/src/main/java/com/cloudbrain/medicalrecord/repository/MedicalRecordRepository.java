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

/**
 * 适配新 medical_record schema（V1 clean design）：
 *   medical_record.id       uuid auto-generated
 *   record_version          替代旧 medical_record_version
 *   access_log              替代旧 medical_record_access_log
 *   去除 period / ai_risk_level / doctor_revision_note / archived_at 列
 */
@Repository
public class MedicalRecordRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalRecord> rowMapper = new MedicalRecordRowMapper();

    public MedicalRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MedicalRecord> findAll() {
        return jdbcTemplate.query(
                "select * from medical_record order by created_at desc", rowMapper);
    }

    public List<MedicalRecord> findAllExcludingCancelledAppointments() {
        return jdbcTemplate.query("""
                select mr.* from medical_record mr
                where not exists (
                    select 1 from appointment.appointment a
                    where a.id = mr.appointment_id::text
                      and a.status = 'CANCELLED'
                )
                order by mr.created_at desc
                """, rowMapper);
    }

    public Optional<MedicalRecord> findById(String id) {
        return jdbcTemplate.query(
                "select * from medical_record where id = ?::uuid", rowMapper, id)
                .stream().findFirst();
    }

    public Optional<MedicalRecord> findByAppointmentId(String appointmentId) {
        return jdbcTemplate.query(
                "select * from medical_record where appointment_id = ?::uuid", rowMapper, appointmentId)
                .stream().findFirst();
    }

    public MedicalRecord save(MedicalRecord record, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                update medical_record set
                    chief_complaint = ?, present_illness = ?, past_history = ?,
                    allergy_history = ?, physical_examination = ?,
                    preliminary_diagnosis = ?, diagnosis = ?, treatment_plan = ?,
                    status = ?, updated_at = now(),
                    diagnosis_created_by_type = ?, diagnosis_ai_record_id = ?,
                    diagnosis_confirmed_by = ?, diagnosis_confirmed_at = ?,
                    version = ?
                where id = ?::uuid and version = ?
                """,
                record.getChiefComplaint(), record.getPresentIllness(), record.getPastHistory(),
                record.getAllergyHistory(), record.getPhysicalExamination(),
                record.getPreliminaryDiagnosis(), record.getDiagnosis(), record.getTreatmentPlan(),
                record.getStatus().name(), record.getDiagnosisCreatedByType(),
                record.getDiagnosisAiRecordId(), record.getDiagnosisConfirmedBy(),
                record.getDiagnosisConfirmedAt(), record.getVersion(),
                record.getId(), expectedVersion);

        if (updated != 1) throw new org.springframework.dao.OptimisticLockingFailureException(
                "病历已被其他窗口更新，请刷新后重试");

        // 写入版本快照（content 用 JSON 字符串）
        jdbcTemplate.update("""
                insert into record_version (record_id, version, content, author_id)
                values (?::uuid, ?, ?::jsonb, ?)
                """,
                record.getId(), record.getVersion(),
                buildVersionJson(record),
                record.getDiagnosisConfirmedBy() != null ? record.getDiagnosisConfirmedBy() : record.getDoctorId());
        return record;
    }

    public List<MedicalRecord> findByPatientId(String patientId) {
        return jdbcTemplate.query(
                "select * from medical_record where patient_id = ?::uuid order by visit_date desc, created_at desc",
                rowMapper, patientId);
    }

    public List<MedicalRecord> findByPatientIdExcludingCancelledAppointments(String patientId) {
        return jdbcTemplate.query("""
                select mr.* from medical_record mr
                where mr.patient_id = ?::uuid
                  and not exists (
                      select 1 from appointment.appointment a
                      where a.id = mr.appointment_id::text and a.status = 'CANCELLED'
                  )
                order by mr.visit_date desc, mr.created_at desc
                """, rowMapper, patientId);
    }

    public void recordAccess(String recordId, String patientId, String actorId,
            String actorRole, String scope, String reason) {
        jdbcTemplate.update("""
                insert into access_log (record_id, patient_id, actor_id, actor_role, scope, reason)
                values (?::uuid, ?::uuid, ?, ?, ?, ?)
                """, recordId, patientId, actorId, actorRole, scope, reason);
    }

    public List<AccessLog> accessLogs(String patientId) {
        StringBuilder sql = new StringBuilder("""
                select id, record_id, patient_id, actor_id, actor_role, scope, reason, accessed_at
                from access_log where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?::uuid");
            args.add(patientId);
        }
        sql.append(" order by accessed_at desc limit 200");
        return jdbcTemplate.query(sql.toString(),
                (rs, row) -> new AccessLog(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("record_id"),
                        rs.getString("patient_id"),
                        rs.getString("actor_id"),
                        rs.getString("actor_role"),
                        rs.getString("scope"),
                        rs.getString("reason"),
                        rs.getTimestamp("accessed_at").toLocalDateTime()),
                args.toArray());
    }

    public void linkReport(String appointmentId, String orderId, String reportId,
            String type, String conclusion, String confirmer, java.time.LocalDateTime confirmedAt) {
        MedicalRecord record = findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("就诊病历不存在"));
        jdbcTemplate.update("""
                insert into medical_record_report_link
                    (medical_record_id, medical_order_id, report_id, report_type, conclusion, confirmed_by, confirmed_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (medical_order_id, report_id) do nothing
                """, record.getId(), orderId, reportId, type, conclusion, confirmer, confirmedAt);
    }

    public MedicalRecord createInitialIfAbsent(MedicalRecord record) {
        // id 由 DB 自动生成（uuid），不显式传入
        jdbcTemplate.update("""
                insert into medical_record (
                    appointment_id, patient_id, patient_name,
                    doctor_id, doctor_name, department_name,
                    visit_date, ai_triage_summary, status, updated_at)
                values (?::uuid, ?::uuid, ?,
                        ?, ?, ?,
                        ?, ?, 'DRAFT', now())
                on conflict (appointment_id) do nothing
                """,
                record.getAppointmentId(), record.getPatientId(), record.getPatientName(),
                record.getDoctorId(), record.getDoctorName(), record.getDepartmentName(),
                record.getVisitDate(), record.getAiTriageSummary());
        return findByAppointmentId(record.getAppointmentId()).orElseThrow();
    }

    public int size() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from medical_record", Integer.class);
        return count == null ? 0 : count;
    }

    private static String buildVersionJson(MedicalRecord r) {
        return String.format("""
                {"chiefComplaint":%s,"presentIllness":%s,"pastHistory":%s,
                 "allergyHistory":%s,"physicalExamination":%s,
                 "preliminaryDiagnosis":%s,"diagnosis":%s,"treatmentPlan":%s,
                 "diagnosisCreatedByType":%s,"status":%s}""",
                jsonStr(r.getChiefComplaint()), jsonStr(r.getPresentIllness()),
                jsonStr(r.getPastHistory()), jsonStr(r.getAllergyHistory()),
                jsonStr(r.getPhysicalExamination()), jsonStr(r.getPreliminaryDiagnosis()),
                jsonStr(r.getDiagnosis()), jsonStr(r.getTreatmentPlan()),
                jsonStr(r.getDiagnosisCreatedByType()), jsonStr(r.getStatus().name()));
    }

    private static String jsonStr(String v) {
        return v == null ? "null" : "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static class MedicalRecordRowMapper implements RowMapper<MedicalRecord> {
        @Override
        public MedicalRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            // period / ai_risk_level 在新 schema 中已移除，传 null
            MedicalRecord record = new MedicalRecord(
                    rs.getString("id"),
                    rs.getString("appointment_id"),
                    rs.getString("patient_id"),
                    rs.getString("patient_name"),
                    rs.getString("doctor_id"),
                    rs.getString("doctor_name"),
                    rs.getString("department_name"),
                    rs.getDate("visit_date") == null ? null : rs.getDate("visit_date").toString(),
                    null,               // period — 新 schema 无此列
                    rs.getString("ai_triage_summary"),
                    null);              // ai_risk_level — 新 schema 无此列
            record.restoreDoctorContent(
                    rs.getString("chief_complaint"),
                    rs.getString("present_illness"),
                    rs.getString("past_history"),
                    rs.getString("allergy_history"),
                    rs.getString("physical_examination"),
                    rs.getString("preliminary_diagnosis"),
                    rs.getString("diagnosis"),
                    rs.getString("treatment_plan"),
                    null,               // doctor_revision_note — 新 schema 无此列
                    MedicalRecordStatus.valueOf(rs.getString("status")),
                    rs.getString("diagnosis_created_by_type"),
                    rs.getString("diagnosis_ai_record_id"),
                    rs.getString("diagnosis_confirmed_by"),
                    rs.getTimestamp("diagnosis_confirmed_at") == null ? null
                            : rs.getTimestamp("diagnosis_confirmed_at").toLocalDateTime(),
                    rs.getLong("version"));
            return record;
        }
    }

    public record AccessLog(java.util.UUID id, String medicalRecordId, String patientId,
            String actorId, String actorRole, String accessScope, String reason,
            java.time.LocalDateTime accessedAt) {}
}
