package com.cloudbrain.audit.service;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AuditLogSearchIndexService {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogSearchIndexService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String baseUrl;
    private final String indexName;
    private RestClient client;
    private volatile boolean indexEnsured;

    public AuditLogSearchIndexService(
            ObjectMapper objectMapper,
            @Value("${search.elasticsearch.enabled:true}") boolean enabled,
            @Value("${search.elasticsearch.base-url:http://localhost:9200}") String baseUrl,
            @Value("${search.elasticsearch.audit-index:cloudbrain-audit-log}") String indexName) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.indexName = indexName;
    }

    @PostConstruct
    void init() {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean index(AuditEvent event) {
        if (!enabled || event == null || blank(event.eventId())) {
            return false;
        }
        AuditActor actor = event.actor();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", event.eventId());
        doc.put("userId", actor == null ? null : actor.userId());
        doc.put("actorName", actor == null ? null : actor.name());
        doc.put("role", actor == null ? null : actor.role());
        doc.put("service", event.service());
        doc.put("resourceType", event.resourceType());
        doc.put("resourceId", event.resourceId());
        doc.put("patientId", event.patientId());
        doc.put("businessId", event.businessId());
        doc.put("action", event.action());
        doc.put("requestIp", actor == null ? null : actor.requestIp());
        doc.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        doc.put("details", event.details() == null ? Map.of() : event.details());
        doc.put("detailsText", toJson(event.details()));
        return indexDocument(event.eventId(), doc);
    }

    public boolean index(AuditLogEntry entry) {
        if (!enabled || entry == null || blank(entry.eventId())) {
            return false;
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", entry.eventId());
        doc.put("userId", entry.userId());
        doc.put("actorName", entry.actorName());
        doc.put("role", entry.role());
        doc.put("service", entry.service());
        doc.put("resourceType", entry.resourceType());
        doc.put("resourceId", entry.resourceId());
        doc.put("patientId", entry.patientId());
        doc.put("businessId", entry.businessId());
        doc.put("action", entry.action());
        doc.put("requestIp", entry.requestIp());
        doc.put("occurredAt", entry.occurredAt() == null ? null : entry.occurredAt().toString());
        doc.put("details", entry.details() == null ? Map.of() : entry.details());
        doc.put("detailsText", toJson(entry.details()));
        return indexDocument(entry.eventId(), doc);
    }

    public Optional<List<String>> searchEventIds(AuditLogSearchCriteria criteria) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            ensureIndex();
            Map<String, Object> response = client.post()
                    .uri("/{index}/_search", indexName)
                    .body(searchBody(criteria))
                    .retrieve()
                    .body(MAP_TYPE);
            return Optional.of(readEventIds(response));
        } catch (RestClientException | IllegalArgumentException error) {
            logger.warn("Audit Elasticsearch search failed, falling back to database: {}", error.getMessage());
            return Optional.empty();
        }
    }

    private boolean indexDocument(String id, Map<String, Object> doc) {
        try {
            ensureIndex();
            client.put()
                    .uri("/{index}/_doc/{id}", indexName, id)
                    .body(doc)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException error) {
            logger.warn("Audit Elasticsearch indexing failed for event {}: {}", id, error.getMessage());
            return false;
        }
    }

    private Map<String, Object> searchBody(AuditLogSearchCriteria criteria) {
        List<Object> filters = new ArrayList<>();
        addTerm(filters, "service", criteria.service());
        addTerm(filters, "action", criteria.action());
        addTerm(filters, "resourceType", criteria.resourceType());
        addTerm(filters, "resourceId", criteria.resourceId());
        addTerm(filters, "userId", criteria.userId());
        addTerm(filters, "patientId", criteria.patientId());
        addTerm(filters, "businessId", criteria.businessId());
        if (criteria.from() != null || criteria.to() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (criteria.from() != null) {
                range.put("gte", criteria.from().toString());
            }
            if (criteria.to() != null) {
                range.put("lte", criteria.to().toString());
            }
            filters.add(Map.of("range", Map.of("occurredAt", range)));
        }

        List<Object> must = new ArrayList<>();
        if (!blank(criteria.keyword())) {
            must.add(Map.of("multi_match", Map.of(
                    "query", criteria.keyword().trim(),
                    "fields", List.of(
                            "eventId^2",
                            "actorName^3",
                            "userId^2",
                            "role",
                            "service",
                            "resourceType",
                            "resourceId",
                            "patientId",
                            "businessId",
                            "action^2",
                            "requestIp",
                            "detailsText"))));
        }
        if (must.isEmpty()) {
            must.add(Map.of("match_all", Map.of()));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", must);
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }
        return Map.of(
                "size", Math.max(1, Math.min(criteria.limit(), 500)),
                "query", Map.of("bool", bool),
                "sort", List.of(Map.of("occurredAt", Map.of("order", "desc"))));
    }

    private void addTerm(List<Object> filters, String field, String value) {
        if (!blank(value)) {
            filters.add(Map.of("term", Map.of(field, value.trim())));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readEventIds(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object hitsObject = response.get("hits");
        if (!(hitsObject instanceof Map<?, ?> hitsMap)) {
            return List.of();
        }
        Object rowsObject = hitsMap.get("hits");
        if (!(rowsObject instanceof List<?> rows)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) {
                continue;
            }
            Object sourceObject = row.get("_source");
            if (sourceObject instanceof Map<?, ?> source && source.get("eventId") != null) {
                ids.add(String.valueOf(source.get("eventId")));
            } else if (row.get("_id") != null) {
                ids.add(String.valueOf(row.get("_id")));
            }
        }
        return ids;
    }

    private void ensureIndex() {
        if (indexEnsured) {
            return;
        }
        synchronized (this) {
            if (indexEnsured) {
                return;
            }
            try {
                client.method(HttpMethod.HEAD)
                        .uri("/{index}", indexName)
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException.NotFound notFound) {
                client.put()
                        .uri("/{index}", indexName)
                        .body(indexDefinition())
                        .retrieve()
                        .toBodilessEntity();
            }
            indexEnsured = true;
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : List.of(
                "eventId", "userId", "role", "service", "resourceType", "resourceId",
                "patientId", "businessId", "action", "requestIp")) {
            properties.put(field, Map.of("type", "keyword"));
        }
        properties.put("actorName", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))));
        properties.put("occurredAt", Map.of("type", "date"));
        properties.put("details", Map.of("type", "object", "enabled", false));
        properties.put("detailsText", Map.of("type", "text"));
        return Map.of("mappings", Map.of("properties", properties));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception error) {
            return "";
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
