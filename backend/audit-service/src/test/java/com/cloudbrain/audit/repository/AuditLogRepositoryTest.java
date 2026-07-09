package com.cloudbrain.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.service.AuditLogSearchCriteria;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AuditLogRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    ObjectMapper objectMapper;

    @Test
    void savePersistsAuditActorFieldsAndDetailsJson() throws Exception {
        when(objectMapper.writeValueAsString(Map.of("amount", "0.01"))).thenReturn("{\"amount\":\"0.01\"}");
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);
        AuditEvent event = new AuditEvent(
                "evt-1",
                "cashier-service",
                "PAYMENT_CONFIRMED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                new AuditActor("cashier-1", "Cashier", "CASHIER", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("amount", "0.01"));

        repository.save(event);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("insert into audit_log");
        assertThat(args.getValue()).containsExactly(
                "evt-1",
                "cashier-1",
                "Cashier",
                "CASHIER",
                "cashier-service",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                "PAYMENT_CONFIRMED",
                "10.0.0.1",
                Timestamp.from(Instant.parse("2026-07-09T10:00:00Z")),
                "{\"amount\":\"0.01\"}");
    }

    @Test
    void saveThrowsWhenDetailsCannotBeSerialized() throws Exception {
        when(objectMapper.writeValueAsString(Map.of("amount", "0.01")))
                .thenThrow(new JsonProcessingException("boom") {});
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);

        assertThatThrownBy(() -> repository.save(new AuditEvent(
                "evt-1",
                "cashier-service",
                "PAYMENT_CONFIRMED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                new AuditActor("cashier-1", "Cashier", "CASHIER", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("amount", "0.01"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize audit details");
    }

    @Test
    void searchBuildsSqlAndMapsRows() throws Exception {
        when(objectMapper.readValue(eq("{\"amount\":\"0.01\"}"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("amount", "0.01"));
        ResultSet resultSet = row(
                1L,
                "evt-1",
                "cashier-1",
                "Cashier",
                "CASHIER",
                "cashier-service",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                "PAYMENT_CONFIRMED",
                "10.0.0.1",
                Instant.parse("2026-07-09T10:00:00Z"),
                "{\"amount\":\"0.01\"}");
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                "cashier-service", "PAYMENT_CONFIRMED", "PAYMENT_ORDER", "payment-1", "cashier-1",
                "patient-1", "biz-1", "wx", Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"), 999);

        List<AuditLogEntry> result = repository.search(criteria);

        assertThat(result).singleElement().satisfies(entry -> {
            assertThat(entry.eventId()).isEqualTo("evt-1");
            assertThat(entry.details()).isEqualTo(Map.of("amount", "0.01"));
        });
    }

    @Test
    void findByEventIdsDeduplicatesInputAndRestoresRequestedOrder() throws Exception {
        when(objectMapper.readValue(eq("{}"), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(Map.of());
        ResultSet first = row(1L, "evt-2", "user-2", "Bob", "ADMIN", "audit-service", "USER", "user-2", null, null, "LOGIN", "10.0.0.2", Instant.parse("2026-07-09T10:00:00Z"), "{}");
        ResultSet second = row(2L, "evt-1", "user-1", "Alice", "ADMIN", "audit-service", "USER", "user-1", null, null, "LOGIN", "10.0.0.1", Instant.parse("2026-07-09T09:00:00Z"), "{}");
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(first, 0), mapper.mapRow(second, 1));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);

        List<AuditLogEntry> result = repository.findByEventIds(List.of("evt-1", "evt-2", "evt-1", " "));

        assertThat(result).extracting(AuditLogEntry::eventId).containsExactly("evt-1", "evt-2");
    }

    @Test
    void findByEventIdsReturnsEmptyWhenNoUsableIdsProvided() {
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);

        assertThat(repository.findByEventIds(java.util.Arrays.asList(" ", null))).isEmpty();
    }

    @Test
    void findRecentForIndexClampsLimitAndMapsRows() throws Exception {
        when(objectMapper.readValue(eq("{}"), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(Map.of());
        ResultSet resultSet = row(1L, "evt-1", "user-1", "Alice", "ADMIN", "audit-service", "USER", "user-1", null, null, "LOGIN", "10.0.0.1", Instant.parse("2026-07-09T09:00:00Z"), "{}");
        doAnswer(invocation -> {
            assertThat((Integer) invocation.getArgument(2)).isEqualTo(20000);
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);

        List<AuditLogEntry> result = repository.findRecentForIndex(50000);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchThrowsWhenStoredDetailsJsonIsInvalid() throws Exception {
        when(objectMapper.readValue(eq("invalid-json"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new JsonProcessingException("boom") {});
        ResultSet resultSet = row(
                1L, "evt-1", "user-1", "Alice", "ADMIN", "audit-service", "USER", "user-1",
                null, null, "LOGIN", "10.0.0.1", Instant.parse("2026-07-09T09:00:00Z"), "invalid-json");
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
        AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, objectMapper);

        assertThatThrownBy(() -> repository.search(new AuditLogSearchCriteria(
                null, null, null, null, null, null, null, null, null, null, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to deserialize audit details");
    }

    private ResultSet row(
            long id,
            String eventId,
            String userId,
            String actorName,
            String role,
            String service,
            String resourceType,
            String resourceId,
            String patientId,
            String businessId,
            String action,
            String requestIp,
            Instant occurredAt,
            String details) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(id);
        when(resultSet.getString("event_id")).thenReturn(eventId);
        when(resultSet.getString("user_id")).thenReturn(userId);
        when(resultSet.getString("actor_name")).thenReturn(actorName);
        when(resultSet.getString("role")).thenReturn(role);
        when(resultSet.getString("service")).thenReturn(service);
        when(resultSet.getString("resource_type")).thenReturn(resourceType);
        when(resultSet.getString("resource_id")).thenReturn(resourceId);
        when(resultSet.getString("patient_id")).thenReturn(patientId);
        when(resultSet.getString("business_id")).thenReturn(businessId);
        when(resultSet.getString("action")).thenReturn(action);
        when(resultSet.getString("request_ip")).thenReturn(requestIp);
        when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(occurredAt));
        when(resultSet.getString("details")).thenReturn(details);
        return resultSet;
    }
}
