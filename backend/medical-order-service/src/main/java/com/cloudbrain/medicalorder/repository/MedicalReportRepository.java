package com.cloudbrain.medicalorder.repository;

import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalReportRepository {
    private final JdbcTemplate jdbc;

    public MedicalReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MedicalAttachment attachment(String orderId, String key, String name, String type,
            long size, String bucket, String actor) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into attachment
                    (id, order_id, object_key, original_name, content_type, size_bytes, bucket, uploaded_by)
                values (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """, id, orderId, key, name, type, size, bucket, actor);
        return attachments(orderId).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    public List<MedicalAttachment> attachments(String orderId) {
        return jdbc.query("""
                select * from attachment
                where order_id = ?::uuid
                order by created_at
                """, (rs, row) -> new MedicalAttachment(
                        rs.getString("id"),
                        rs.getString("order_id"),
                        rs.getString("object_key"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("bucket"),
                        rs.getString("uploaded_by"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                orderId);
    }

    public AiMedicalTask createTask(String orderId, String externalId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into ai_medical_task(id, medical_order_id, external_task_id, task_type, status)
                values (?, ?, ?, 'CT_ANALYSIS', 'PENDING')
                """, id, orderId, externalId);
        return taskByExternal(externalId).orElseThrow();
    }

    public Optional<AiMedicalTask> taskByExternal(String id) {
        return jdbc.query("select * from ai_medical_task where external_task_id = ?",
                (rs, row) -> task(rs), id).stream().findFirst();
    }

    public Optional<AiMedicalTask> timeoutTaskIfExpired(String externalId, long timeoutSeconds) {
        int updated = jdbc.update("""
                update ai_medical_task
                set status = 'FAILED',
                    raw_output = '{}'::jsonb,
                    error_message = ?,
                    updated_at = now()
                where external_task_id = ?
                  and status in ('PENDING','RUNNING')
                  and created_at < now() - (? * interval '1 second')
                """,
                "AI analysis timed out. Please check the AI service logs and retry.",
                externalId,
                timeoutSeconds);
        return updated > 0 ? taskByExternal(externalId) : Optional.empty();
    }

    public AiMedicalTask updateTask(String externalId, String status, String modelVersion,
            String output, String error) {
        jdbc.update("""
                update ai_medical_task
                set status = ?, model_version = ?, raw_output = ?::jsonb, error_message = ?, updated_at = now()
                where external_task_id = ?
                """, normalizeTaskStatus(status), modelVersion, output == null ? "{}" : output, error, externalId);
        return taskByExternal(externalId).orElseThrow();
    }

    public MedicalReport saveDraft(String orderId, String type, String findings, String conclusion,
            String advice, String source, String aiTaskId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into medical_report
                    (id, order_id, report_type, status, findings, conclusion, advice,
                     created_by_type, ai_task_id, ai_original_findings, ai_original_conclusion)
                values (?::uuid, ?::uuid, ?, 'DRAFT', ?, ?, ?, ?, ?::uuid, ?, ?)
                on conflict (order_id) do update set
                    status = case when medical_report.status = 'CONFIRMED' then medical_report.status else 'DRAFT' end,
                    findings = case when medical_report.status = 'CONFIRMED' then medical_report.findings else excluded.findings end,
                    conclusion = case when medical_report.status = 'CONFIRMED' then medical_report.conclusion else excluded.conclusion end,
                    advice = case when medical_report.status = 'CONFIRMED' then medical_report.advice else excluded.advice end,
                    created_by_type = case when medical_report.status = 'CONFIRMED' then medical_report.created_by_type else excluded.created_by_type end,
                    ai_task_id = case when medical_report.status = 'CONFIRMED' then medical_report.ai_task_id else excluded.ai_task_id end,
                    ai_original_findings = coalesce(medical_report.ai_original_findings, excluded.ai_original_findings),
                    ai_original_conclusion = coalesce(medical_report.ai_original_conclusion, excluded.ai_original_conclusion),
                    updated_at = now()
                """, id, orderId, type, findings, conclusion, advice, source, aiTaskId,
                "AI".equals(source) ? findings : null,
                "AI".equals(source) ? conclusion : null);
        return reportByOrder(orderId).orElseThrow();
    }

    public Optional<MedicalReport> reportByOrder(String orderId) {
        return jdbc.query("select * from medical_report where order_id = ?::uuid",
                (rs, row) -> report(rs), orderId).stream().findFirst();
    }

    public List<MedicalReport> reports() {
        return jdbc.query("select * from medical_report order by updated_at desc",
                (rs, row) -> report(rs));
    }

    public MedicalReport confirm(String orderId, String findings, String conclusion,
            String advice, String doctor) {
        if (jdbc.update("""
                update medical_report
                set status = 'CONFIRMED',
                    findings = ?,
                    conclusion = ?,
                    advice = ?,
                    modified_from_ai = (created_by_type = 'AI'
                        and (findings is distinct from ? or conclusion is distinct from ?)),
                    confirmed_by = ?,
                    confirmed_at = now(),
                    updated_at = now()
                where order_id = ?::uuid and status = 'DRAFT'
                """, findings, conclusion, advice, findings, conclusion, doctor, orderId) != 1) {
            Optional<MedicalReport> existing = reportByOrder(orderId);
            if (existing.isPresent() && "CONFIRMED".equals(existing.get().status())) return existing.get();
            throw new IllegalStateException("report does not exist or cannot be confirmed");
        }
        return reportByOrder(orderId).orElseThrow();
    }

    public MedicalReport reject(String orderId, String doctor, String reason) {
        if (jdbc.update("""
                update medical_report
                set status = 'REJECTED',
                    rejected_by = ?,
                    rejected_at = now(),
                    rejection_reason = ?,
                    updated_at = now()
                where order_id = ?::uuid and status = 'DRAFT'
                """, doctor, reason, orderId) != 1) {
            throw new IllegalStateException("report cannot be rejected in current status");
        }
        return reportByOrder(orderId).orElseThrow();
    }

    private AiMedicalTask task(ResultSet rs) throws SQLException {
        return new AiMedicalTask(
                rs.getString("id"),
                rs.getString("medical_order_id"),
                rs.getString("external_task_id"),
                rs.getString("task_type"),
                rs.getString("status"),
                rs.getString("model_version"),
                rs.getString("raw_output"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private MedicalReport report(ResultSet rs) throws SQLException {
        return new MedicalReport(
                rs.getString("id"),
                rs.getString("order_id"),
                rs.getString("report_type"),
                rs.getString("status"),
                rs.getString("findings"),
                rs.getString("conclusion"),
                rs.getString("advice"),
                rs.getString("created_by_type"),
                rs.getString("ai_task_id"),
                rs.getString("ai_original_findings"),
                rs.getString("ai_original_conclusion"),
                rs.getBoolean("modified_from_ai"),
                rs.getString("confirmed_by"),
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime(),
                rs.getString("rejection_reason"),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private String normalizeTaskStatus(String status) {
        return switch (status == null ? "" : status) {
            case "RUNNING" -> "RUNNING";
            case "COMPLETED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            default -> "PENDING";
        };
    }
}
