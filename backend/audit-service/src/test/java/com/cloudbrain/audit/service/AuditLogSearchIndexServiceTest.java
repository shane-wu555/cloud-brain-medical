package com.cloudbrain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditLogSearchIndexServiceTest {
    @Test
    void indexReturnsFalseWhenServiceDisabledOrEventInvalid() {
        AuditLogSearchIndexService disabled = new AuditLogSearchIndexService(new ObjectMapper(), false, "http://localhost:9200", "audit-index");
        AuditLogSearchIndexService enabled = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");

        assertThat(disabled.index(event("evt-1"))).isFalse();
        assertThat(enabled.index((AuditEvent) null)).isFalse();
        assertThat(enabled.index(event(" "))).isFalse();
        assertThat(enabled.index((AuditLogEntry) null)).isFalse();
        assertThat(enabled.index(entry(" "))).isFalse();
    }

    @Test
    void searchEventIdsReturnsEmptyWhenServiceDisabled() {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), false, "http://localhost:9200", "audit-index");

        Optional<List<String>> result = service.searchEventIds(new AuditLogSearchCriteria(
                "audit-service", "LOGIN", "USER", "user-1", "user-1",
                "patient-1", "biz-1", "keyword", Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"), 20));

        assertThat(result).isEmpty();
    }

    @Test
    void searchBodyIncludesKeywordFiltersAndRange() throws Exception {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) invoke(
                service,
                "searchBody",
                new Class<?>[] {AuditLogSearchCriteria.class},
                new AuditLogSearchCriteria(
                        "audit-service", "LOGIN", "USER", "user-1", "operator-1",
                        "patient-1", "biz-1", " keyword ", Instant.parse("2026-07-09T00:00:00Z"),
                        Instant.parse("2026-07-10T00:00:00Z"), 600));

        assertThat(body.get("size")).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) body.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertThat((List<?>) bool.get("must")).hasSize(1);
        assertThat((List<?>) bool.get("filter")).hasSize(8);
    }

    @Test
    void readEventIdsPrefersSourceEventIdAndFallsBackToDocumentId() throws Exception {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");
        Map<String, Object> response = Map.of(
                "hits", Map.of(
                        "hits", List.of(
                                Map.of("_source", Map.of("eventId", "evt-1")),
                                Map.of("_id", "evt-2"),
                                Map.of())));

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) invoke(service, "readEventIds", new Class<?>[] {Map.class}, response);

        assertThat(ids).containsExactly("evt-1", "evt-2");
    }

    @Test
    void indexDefinitionContainsExpectedMappings() throws Exception {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");

        @SuppressWarnings("unchecked")
        Map<String, Object> definition = (Map<String, Object>) invoke(service, "indexDefinition", new Class<?>[0]);

        @SuppressWarnings("unchecked")
        Map<String, Object> mappings = (Map<String, Object>) definition.get("mappings");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
        assertThat(properties).containsKeys("eventId", "actorName", "occurredAt", "details", "detailsText");
    }

    @Test
    void toJsonFallsBackToEmptyStringOnSerializationError() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(Map.of("amount", BigDecimal.ONE)))
                .thenThrow(new JsonProcessingException("boom") {});
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(objectMapper, true, "http://localhost:9200", "audit-index");

        String json = (String) invoke(service, "toJson", new Class<?>[] {Map.class}, Map.of("amount", BigDecimal.ONE));

        assertThat(json).isEmpty();
    }

    @Test
    void reflectiveInvocationSurfacesUnexpectedErrors() {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");

        assertThatThrownBy(() -> invoke(service, "missingMethod", new Class<?>[0]))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private AuditEvent event(String eventId) {
        return new AuditEvent(
                eventId,
                "audit-service",
                "LOGIN",
                "USER",
                "user-1",
                "patient-1",
                "biz-1",
                new AuditActor("user-1", "Alice", "ADMIN", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("result", "SUCCESS"));
    }

    private AuditLogEntry entry(String eventId) {
        return new AuditLogEntry(
                1L,
                eventId,
                "user-1",
                "Alice",
                "ADMIN",
                "audit-service",
                "USER",
                "user-1",
                "patient-1",
                "biz-1",
                "LOGIN",
                "10.0.0.1",
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("result", "SUCCESS"));
    }
}
