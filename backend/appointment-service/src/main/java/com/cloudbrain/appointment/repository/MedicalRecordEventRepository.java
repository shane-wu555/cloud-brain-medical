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
                    insert into integration_event (id, aggregate_id, event_type, payload, status, next_attempt_at)
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
                    insert into integration_event (id, aggregate_id, event_type, payload, status, next_attempt_at)
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
                    select id from integration_event
                    where ((status in ('PENDING', 'RETRY') and next_attempt_at <= now())
                           or (status = 'PROCESSING' and next_attempt_at <= now()))
                    order by created_at
                    limit ? for update skip locked
                )
                update integration_event event
                set status = 'PROCESSING', next_attempt_at = now() + interval '5 minutes'
                from candidates
                where event.id = candidates.id
                returning event.id, event.event_type, event.payload::text, event.retry_count
                """, (rs, rowNum) -> new PendingEvent(
                rs.getString("id"), rs.getString("event_type"), rs.getString("payload"), rs.getInt("retry_count")), limit);
    }

    public void markCompleted(String id) {
        jdbcTemplate.update("update integration_event set status = 'COMPLETED', completed_at = now(), last_error = null where id = ?", id);
    }

    public void markFailed(String id, int retryCount, String error) {
        String status = retryCount >= 9 ? "FAILED" : "RETRY";
        jdbcTemplate.update("""
                update integration_event
                set status = ?, retry_count = retry_count + 1, last_error = ?,
                    next_attempt_at = now() + ((retry_count + 1) * interval '30 seconds')
                where id = ?
                """, status, error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 1000)), id);
    }

    public record PendingEvent(String id, String eventType, String payload, int retryCount) {}
}
