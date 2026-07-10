package com.cloudbrain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class AuditLogSearchIndexServiceTest {
    @Mock
    RestClient client;

    @Mock
    RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    RestClient.RequestBodySpec requestBodySpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

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
    void indexAuditEventStoresDocumentWhenIndexAlreadyExists() {
        AuditLogSearchIndexService service = serviceWithClient();
        AuditEvent event = event("evt-1");
        when(client.method(HttpMethod.HEAD)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}", "audit-index")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        when(client.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}/_doc/{id}", "audit-index", "evt-1")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);

        boolean indexed = service.index(event);

        assertThat(indexed).isTrue();
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .containsEntry("eventId", "evt-1")
                .containsEntry("actorName", "Alice")
                .containsEntry("requestIp", "10.0.0.1");
        assertThat(bodyCaptor.getValue().get("detailsText")).isEqualTo("{\"result\":\"SUCCESS\"}");
    }

    @Test
    void indexAuditLogEntryCreatesIndexWhenMissing() {
        AuditLogSearchIndexService service = serviceWithClient();
        AuditLogEntry entry = entry("evt-2");
        RestClient.RequestBodyUriSpec createIndexSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec createIndexBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec createIndexResponse = mock(RestClient.ResponseSpec.class);
        RestClient.RequestBodyUriSpec indexDocSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec indexDocBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec indexDocResponse = mock(RestClient.ResponseSpec.class);

        when(client.method(HttpMethod.HEAD)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}", "audit-index")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        when(client.put()).thenReturn(createIndexSpec, indexDocSpec);
        when(createIndexSpec.uri("/{index}", "audit-index")).thenReturn(createIndexBodySpec);
        when(createIndexBodySpec.body(anyMap())).thenReturn(createIndexBodySpec);
        when(createIndexBodySpec.retrieve()).thenReturn(createIndexResponse);
        when(createIndexResponse.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        when(indexDocSpec.uri("/{index}/_doc/{id}", "audit-index", "evt-2")).thenReturn(indexDocBodySpec);
        when(indexDocBodySpec.body(anyMap())).thenReturn(indexDocBodySpec);
        when(indexDocBodySpec.retrieve()).thenReturn(indexDocResponse);
        when(indexDocResponse.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        boolean indexed = service.index(entry);

        assertThat(indexed).isTrue();
        ArgumentCaptor<Map<String, Object>> createBodyCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> docBodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createIndexBodySpec).body(createBodyCaptor.capture());
        verify(indexDocBodySpec).body(docBodyCaptor.capture());
        assertThat(createBodyCaptor.getValue()).containsKey("mappings");
        assertThat(docBodyCaptor.getValue())
                .containsEntry("eventId", "evt-2")
                .containsEntry("service", "audit-service")
                .containsEntry("action", "LOGIN");
    }

    @Test
    void indexReturnsFalseWhenIndexRequestFails() {
        AuditLogSearchIndexService service = serviceWithClient();
        ReflectionTestUtils.setField(service, "indexEnsured", true);
        AuditEvent event = event("evt-3");
        when(client.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}/_doc/{id}", "audit-index", "evt-3")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new RestClientException("boom"));

        assertThat(service.index(event)).isFalse();
    }

    @Test
    void searchEventIdsReturnsParsedIdsWhenSearchSucceeds() {
        AuditLogSearchIndexService service = serviceWithClient();
        ReflectionTestUtils.setField(service, "indexEnsured", true);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                "audit-service", "LOGIN", "USER", "user-1", "operator-1",
                "patient-1", "biz-1", " keyword ", Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"), 600);
        Map<String, Object> searchResponse = Map.of(
                "hits", Map.of("hits", List.of(
                        Map.of("_source", Map.of("eventId", "evt-1")),
                        Map.of("_id", "evt-2"))));

        when(client.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}/_search", "audit-index")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(searchResponse);

        Optional<List<String>> result = service.searchEventIds(criteria);

        assertThat(result).contains(List.of("evt-1", "evt-2"));
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) bodyCaptor.getValue().get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertThat(bodyCaptor.getValue().get("size")).isEqualTo(500);
        assertThat(bool).containsKeys("must", "filter");
    }

    @Test
    void searchEventIdsUsesMatchAllAndReturnsEmptyOnClientFailure() {
        AuditLogSearchIndexService service = serviceWithClient();
        ReflectionTestUtils.setField(service, "indexEnsured", true);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                null, null, null, null, null, null, null, "   ", null, null, 0);

        when(client.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}/_search", "audit-index")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenThrow(new RestClientException("search failed"));

        assertThat(service.searchEventIds(criteria)).isEmpty();
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().get("size")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) bodyCaptor.getValue().get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertThat(((List<?>) bool.get("must")).get(0)).isEqualTo(Map.of("match_all", Map.of()));
    }

    @Test
    void ensureIndexRunsOnlyOnceAfterSuccessfulInitialization() {
        AuditLogSearchIndexService service = serviceWithClient();
        AuditEvent first = event("evt-4");
        AuditEvent second = event("evt-5");
        when(client.method(HttpMethod.HEAD)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/{index}", "audit-index")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        when(client.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/{index}/_doc/{id}"), eq("audit-index"), any()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);

        assertThat(service.index(first)).isTrue();
        assertThat(service.index(second)).isTrue();

        verify(client, times(1)).method(HttpMethod.HEAD);
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

    private AuditLogSearchIndexService serviceWithClient() {
        AuditLogSearchIndexService service = new AuditLogSearchIndexService(new ObjectMapper(), true, "http://localhost:9200", "audit-index");
        ReflectionTestUtils.setField(service, "client", client);
        return service;
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
