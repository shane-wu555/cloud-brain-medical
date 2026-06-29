package com.cloudbrain.appointment.repository;

import com.cloudbrain.appointment.entity.Appointment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalRecordEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MedicalRecordEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueueMedicalRecord(Appointment appointment) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "appointmentId", appointment.getId(),
                    "patientId", appointment.getPatientId(),
                    "patientName", appointment.getPatientName(),
                    "doctorId", appointment.getDoctorId(),
                    "doctorName", appointment.getDoctorName(),
                    "departmentName", appointment.getDepartmentName(),
                    "triageSummary", appointment.getTriageSummary() == null ? "" : appointment.getTriageSummary(),
                    "riskLevel", appointment.getRiskLevel(),
                    "visitDate", appointment.getVisitDate().toString(),
                    "period", appointment.getPeriod()));
            jdbcTemplate.update("""
                    insert into outbox_event (id, aggregate_id, event_type, payload, status, next_attempt_at)
                    values (?, ?, 'APPOINTMENT_PAID', ?::jsonb, 'PENDING', now())
                    on conflict (aggregate_id, event_type) do nothing
                    """, "event-" + UUID.randomUUID(), appointment.getId(), payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("病历创建事件序列化失败", exception);
        }
    }

    public void enqueuePayment(Appointment appointment, java.math.BigDecimal amount, String operatorId) {
        enqueueBusinessEvent(appointment.getId(), "PAYMENT_COMPLETED", Map.of(
                "businessType", "APPOINTMENT", "businessId", appointment.getId(),
                "patientId", appointment.getPatientId(), "amount", amount,
                "paymentMethod", appointment.getPaymentMethod(), "operatorId", operatorId));
    }

    public void enqueueRefund(Appointment appointment, java.math.BigDecimal amount, String operatorId) {
        enqueueBusinessEvent(appointment.getId(), "REFUND_COMPLETED", Map.of(
                "businessType", "APPOINTMENT", "businessId", appointment.getId(),
                "patientId", appointment.getPatientId(), "amount", amount,
                "reason", "取消挂号", "operatorId", operatorId));
    }

    private void enqueueBusinessEvent(String aggregateId, String eventType, Map<String, Object> payload) {
        try {
            jdbcTemplate.update("""
                    insert into outbox_event (id, aggregate_id, event_type, payload, status, next_attempt_at)
                    values (?, ?, ?, ?::jsonb, 'PENDING', now())
                    on conflict (aggregate_id, event_type) do nothing
                    """, "event-" + UUID.randomUUID(), aggregateId, eventType, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务事件序列化失败", exception);
        }
    }

    public List<PendingEvent> findPending(int limit) {
        return jdbcTemplate.query("""
                with candidates as (
                    select id from outbox_event
                    where ((status in ('PENDING', 'RETRY') and next_attempt_at <= now())
                           or (status = 'PROCESSING' and next_attempt_at <= now()))
                    order by created_at
                    limit ? for update skip locked
                )
                update outbox_event ev
                set status = 'PROCESSING', next_attempt_at = now() + interval '5 minutes'
                from candidates
                where ev.id = candidates.id
                returning ev.id, ev.event_type, ev.payload::text, ev.retry_count
                """, (rs, rowNum) -> new PendingEvent(
                rs.getString("id"), rs.getString("event_type"), rs.getString("payload"), rs.getInt("retry_count")), limit);
    }

    public void markCompleted(String id) {
        jdbcTemplate.update("update outbox_event set status = 'COMPLETED', completed_at = now(), last_error = null where id = ?", id);
    }

    public void markFailed(String id, int retryCount, String error) {
        String status = retryCount >= 9 ? "FAILED" : "RETRY";
        jdbcTemplate.update("""
                update outbox_event
                set status = ?, retry_count = retry_count + 1, last_error = ?,
                    next_attempt_at = now() + ((retry_count + 1) * interval '30 seconds')
                where id = ?
                """, status, error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 1000)), id);
    }

    public List<EventView> findEvents(String status) {
        return jdbcTemplate.query("""
                select id,aggregate_id,event_type,status,retry_count,next_attempt_at,last_error,created_at,completed_at
                from outbox_event where (? is null or status=?) order by created_at desc limit 200
                """, (rs, row) -> new EventView(rs.getString("id"), rs.getString("aggregate_id"), rs.getString("event_type"),
                rs.getString("status"), rs.getInt("retry_count"), rs.getTimestamp("next_attempt_at").toLocalDateTime(),
                rs.getString("last_error"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime()),
                status, status);
    }

    public void retry(String id) {
        if (jdbcTemplate.update("""
                update outbox_event set status='PENDING', next_attempt_at=now(), last_error=null
                where id=? and status in ('FAILED','RETRY')
                """, id) != 1) throw new IllegalArgumentException("事件不存在或当前状态不可重试");
    }

    public record PendingEvent(String id, String eventType, String payload, int retryCount) {}
    public record EventView(String id, String aggregateId, String eventType, String status, int retryCount,
            java.time.LocalDateTime nextAttemptAt, String lastError, java.time.LocalDateTime createdAt,
            java.time.LocalDateTime completedAt) {}
}
