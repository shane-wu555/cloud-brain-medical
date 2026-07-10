package com.cloudbrain.patient.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PatientNotification(
            String id, String patientId, String category, String title, String body,
            String referenceType, String referenceId, boolean isRead,
            OffsetDateTime readAt, OffsetDateTime createdAt) {}

    public record CreateRequest(
            String patientId, String category, String title, String body,
            String referenceType, String referenceId) {}

    @Transactional
    public PatientNotification create(CreateRequest req) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into patient_notification (id, patient_id, category, title, body, reference_type, reference_id)
                values (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                """, id, req.patientId(), req.category(), req.title(),
                req.body() == null ? "" : req.body(), req.referenceType(), req.referenceId());
        return findById(id);
    }

    public Map<String, Integer> unreadCount(String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select category, count(*) as cnt
                from patient_notification
                where patient_id = ?::uuid and is_read = false
                group by category
                """, patientId);
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (Map<String, Object> row : rows) {
            String category = (String) row.get("category");
            int cnt = ((Number) row.get("cnt")).intValue();
            counts.put(category, cnt);
            total += cnt;
        }
        counts.put("total", total);
        return counts;
    }

    public List<PatientNotification> list(String patientId, String category, int limit, int offset) {
        if (category != null && !category.isBlank()) {
            return jdbc.query("""
                    select * from patient_notification
                    where patient_id = ?::uuid and category = ?
                    order by created_at desc
                    limit ? offset ?
                    """, (rs, row) -> map(rs), patientId, category, limit, offset);
        }
        return jdbc.query("""
                select * from patient_notification
                where patient_id = ?::uuid
                order by created_at desc
                limit ? offset ?
                """, (rs, row) -> map(rs), patientId, limit, offset);
    }

    public boolean markRead(String notificationId, String patientId) {
        int updated = jdbc.update("""
                update patient_notification set is_read = true, read_at = now()
                where id = ?::uuid and patient_id = ?::uuid and is_read = false
                """, notificationId, patientId);
        return updated > 0;
    }

    public int markAllRead(String patientId, String category) {
        if (category != null && !category.isBlank()) {
            return jdbc.update("""
                    update patient_notification set is_read = true, read_at = now()
                    where patient_id = ?::uuid and category = ? and is_read = false
                    """, patientId, category);
        }
        return jdbc.update("""
                update patient_notification set is_read = true, read_at = now()
                where patient_id = ?::uuid and is_read = false
                """, patientId);
    }

    private PatientNotification findById(String id) {
        return jdbc.query("select * from patient_notification where id = ?::uuid",
                (rs, row) -> map(rs), id)
                .stream().findFirst().orElseThrow(() ->
                        new IllegalStateException("Notification not found after insert"));
    }

    private PatientNotification map(ResultSet rs) throws SQLException {
        return new PatientNotification(
                rs.getString("id"),
                rs.getString("patient_id"),
                rs.getString("category"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getBoolean("is_read"),
                rs.getObject("read_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
