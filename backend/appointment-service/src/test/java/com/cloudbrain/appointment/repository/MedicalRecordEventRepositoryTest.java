package com.cloudbrain.appointment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class MedicalRecordEventRepositoryTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock ObjectMapper objectMapper;

    @Test
    void enqueueMedicalRecordSerializesPayloadAndPersistsEvent() throws Exception {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"appointmentId\":\"appt-1\"}");

        repository.enqueueMedicalRecord(appointment());

        verify(jdbcTemplate).update(contains("insert into outbox_event"), anyString(), eq("appt-1"), eq("{\"appointmentId\":\"appt-1\"}"));
    }

    @Test
    void enqueueMedicalRecordWrapsSerializationFailure() throws Exception {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> repository.enqueueMedicalRecord(appointment())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enqueuePaymentAndRefundPersistBusinessEvents() throws Exception {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        Appointment appointment = appointment();
        appointment.markPaid("WECHAT");

        repository.enqueuePayment(appointment, new BigDecimal("12.30"), "cashier-1");
        repository.enqueueRefund(appointment, new BigDecimal("6.10"), "cashier-1");

        verify(jdbcTemplate).update(contains("insert into outbox_event"), anyString(), eq("appt-1"), eq("PAYMENT_COMPLETED"), eq("{\"ok\":true}"));
        verify(jdbcTemplate).update(contains("insert into outbox_event"), anyString(), eq("appt-1"), eq("REFUND_COMPLETED"), eq("{\"ok\":true}"));
    }

    @Test
    void findPendingMapsPendingEvents() throws Exception {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        when(jdbcTemplate.query(contains("with candidates as"), any(RowMapper.class), eq(20)))
                .thenAnswer(invocation -> List.of(mapPending(invocation.getArgument(1), values(
                        "id", "evt-1",
                        "event_type", "APPOINTMENT_PAID",
                        "payload", "{\"a\":1}",
                        "retry_count", 2))));

        List<MedicalRecordEventRepository.PendingEvent> events = repository.findPending(20);

        assertThat(events).containsExactly(new MedicalRecordEventRepository.PendingEvent("evt-1", "APPOINTMENT_PAID", "{\"a\":1}", 2));
    }

    @Test
    void markCompletedAndRetryDelegateToJdbc() {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        when(jdbcTemplate.update(contains("status = 'COMPLETED'"), eq("evt-1"))).thenReturn(1);
        when(jdbcTemplate.update(contains("update outbox_event set status='PENDING'"), eq("evt-1"))).thenReturn(1);
        when(jdbcTemplate.update(contains("update outbox_event set status='PENDING'"), eq("missing"))).thenReturn(0);

        repository.markCompleted("evt-1");
        repository.retry("evt-1");
        assertThatThrownBy(() -> repository.retry("missing")).isInstanceOf(IllegalArgumentException.class);
        verify(jdbcTemplate).update(contains("status = 'COMPLETED'"), eq("evt-1"));
    }

    @Test
    void markFailedUsesRetryOrFailedAndTruncatesError() {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        String longError = "x".repeat(1200);

        repository.markFailed("evt-1", 3, null);
        repository.markFailed("evt-2", 9, longError);

        verify(jdbcTemplate).update(contains("update outbox_event"), eq("RETRY"), eq("unknown error"), eq("evt-1"));
        verify(jdbcTemplate).update(contains("update outbox_event"), eq("FAILED"), eq(longError.substring(0, 1000)), eq("evt-2"));
    }

    @Test
    void findEventsMapsCompletedAndIncompleteEvents() throws Exception {
        MedicalRecordEventRepository repository = new MedicalRecordEventRepository(jdbcTemplate, objectMapper);
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 10, 0);
        when(jdbcTemplate.query(contains("select id,aggregate_id,event_type,status"), any(RowMapper.class), eq("FAILED"), eq("FAILED")))
                .thenAnswer(invocation -> List.of(mapEvent(invocation.getArgument(1), values(
                        "id", "evt-1",
                        "aggregate_id", "appt-1",
                        "event_type", "APPOINTMENT_PAID",
                        "status", "FAILED",
                        "retry_count", 2,
                        "next_attempt_at", now,
                        "last_error", "boom",
                        "created_at", now.minusMinutes(1),
                        "completed_at", null))));

        List<MedicalRecordEventRepository.EventView> events = repository.findEvents("FAILED");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).completedAt()).isNull();
        assertThat(events.get(0).status()).isEqualTo("FAILED");
    }

    @SuppressWarnings("unchecked")
    private MedicalRecordEventRepository.PendingEvent mapPending(Object mapperObject, Map<String, Object> values) throws SQLException {
        RowMapper<MedicalRecordEventRepository.PendingEvent> mapper = (RowMapper<MedicalRecordEventRepository.PendingEvent>) mapperObject;
        return mapper.mapRow(resultSet(values), 0);
    }

    @SuppressWarnings("unchecked")
    private MedicalRecordEventRepository.EventView mapEvent(Object mapperObject, Map<String, Object> values) throws SQLException {
        RowMapper<MedicalRecordEventRepository.EventView> mapper = (RowMapper<MedicalRecordEventRepository.EventView>) mapperObject;
        return mapper.mapRow(resultSet(values), 0);
    }

    private ResultSet resultSet(Map<String, Object> values) throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0, String.class));
            return value == null ? null : String.valueOf(value);
        });
        when(resultSet.getInt("retry_count")).thenReturn((Integer) values.getOrDefault("retry_count", 0));
        if (values.containsKey("next_attempt_at")) {
            when(resultSet.getTimestamp("next_attempt_at")).thenReturn(Timestamp.valueOf((LocalDateTime) values.get("next_attempt_at")));
        }
        if (values.containsKey("created_at")) {
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf((LocalDateTime) values.get("created_at")));
        }
        Object completedAt = values.get("completed_at");
        if (values.containsKey("completed_at")) {
            when(resultSet.getTimestamp("completed_at")).thenReturn(
                    completedAt == null ? null : Timestamp.valueOf((LocalDateTime) completedAt));
        }
        return resultSet;
    }

    private Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }

    private Appointment appointment() {
        return new Appointment(
                "appt-1",
                "slot-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.of(2026, 7, 9),
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                AppointmentStatus.WAITING,
                PaymentStatus.PAID,
                null,
                "LOW",
                null,
                1);
    }
}
