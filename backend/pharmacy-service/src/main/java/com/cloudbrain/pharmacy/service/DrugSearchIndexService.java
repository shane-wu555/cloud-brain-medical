package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
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
public class DrugSearchIndexService {
    private static final Logger logger = LoggerFactory.getLogger(DrugSearchIndexService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final boolean enabled;
    private final String baseUrl;
    private final String indexName;
    private RestClient client;
    private volatile boolean indexEnsured;

    public DrugSearchIndexService(
            @Value("${search.elasticsearch.enabled:true}") boolean enabled,
            @Value("${search.elasticsearch.base-url:http://localhost:9200}") String baseUrl,
            @Value("${search.elasticsearch.drug-index:cloudbrain-drugs}") String indexName) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.indexName = indexName;
    }

    @PostConstruct
    void init() {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean index(PharmacyRepository.Drug drug) {
        if (!enabled || drug == null || blank(drug.id())) {
            return false;
        }
        try {
            ensureIndex();
            client.put()
                    .uri("/{index}/_doc/{id}?refresh=wait_for", indexName, drug.id())
                    .body(document(drug))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException error) {
            logger.warn("Drug Elasticsearch indexing failed for drug {}: {}", drug.id(), error.getMessage());
            return false;
        }
    }

    public int reindex(List<PharmacyRepository.Drug> drugs) {
        if (!enabled || drugs == null || drugs.isEmpty()) {
            return 0;
        }
        int indexed = 0;
        for (PharmacyRepository.Drug drug : drugs) {
            if (index(drug)) {
                indexed += 1;
            }
        }
        if (indexed > 0) {
            refreshIndex();
        }
        return indexed;
    }

    public Optional<List<String>> searchDrugIds(String keyword, String storageCondition, int limit) {
        if (!enabled || blank(keyword)) {
            return Optional.empty();
        }
        try {
            ensureIndex();
            Map<String, Object> response = client.post()
                    .uri("/{index}/_search", indexName)
                    .body(searchBody(keyword, storageCondition, limit))
                    .retrieve()
                    .body(MAP_TYPE);
            return Optional.of(readIds(response));
        } catch (RestClientException | IllegalArgumentException error) {
            logger.warn("Drug Elasticsearch search failed, falling back to database: {}", error.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> document(PharmacyRepository.Drug drug) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", drug.id());
        doc.put("drugCode", drug.drugCode());
        doc.put("drugName", drug.drugName());
        doc.put("specification", drug.specification());
        doc.put("unit", drug.unit());
        doc.put("unitPrice", drug.unitPrice());
        doc.put("dosageForm", drug.dosageForm());
        doc.put("storageCondition", drug.storageCondition());
        doc.put("quantity", drug.quantity());
        doc.put("warningThreshold", drug.warningThreshold());
        doc.put("lowStock", drug.quantity() <= drug.warningThreshold());
        return doc;
    }

    private Map<String, Object> searchBody(String keyword, String storageCondition, int limit) {
        List<Object> filters = new ArrayList<>();
        if (!blank(storageCondition)) {
            filters.add(Map.of("term", Map.of("storageCondition", storageCondition.trim())));
        }
        String normalizedKeyword = keyword.trim();
        List<Object> should = List.of(
                Map.of("match_phrase", Map.of(
                        "drugName", Map.of(
                                "query", normalizedKeyword,
                                "boost", 8))),
                Map.of("multi_match", Map.of(
                        "query", normalizedKeyword,
                        "fields", List.of(
                                "drugName^4",
                                "drugCode.text^3",
                                "specification^2",
                                "dosageForm",
                                "storageCondition"),
                        "fuzziness", "AUTO",
                        "prefix_length", 1,
                        "lenient", true)),
                Map.of("wildcard", Map.of(
                        "drugCode", Map.of(
                                "value", "*" + normalizedKeyword + "*",
                                "case_insensitive", true,
                                "boost", 4))),
                Map.of("wildcard", Map.of(
                        "drugName.keyword", Map.of(
                                "value", "*" + normalizedKeyword + "*",
                                "case_insensitive", true,
                                "boost", 6))),
                Map.of("wildcard", Map.of(
                        "dosageForm.keyword", Map.of(
                                "value", "*" + normalizedKeyword + "*",
                                "case_insensitive", true,
                                "boost", 2))));
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of("bool", Map.of(
                "should", should,
                "minimum_should_match", 1))));
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }
        return Map.of(
                "size", Math.max(1, Math.min(limit, 200)),
                "min_score", 1.0,
                "query", Map.of("bool", bool),
                "sort", List.of(
                        Map.of("_score", Map.of("order", "desc")),
                        Map.of("drugCode", Map.of("order", "asc"))));
    }

    @SuppressWarnings("unchecked")
    private List<String> readIds(Map<String, Object> response) {
        if (response == null || !(response.get("hits") instanceof Map<?, ?> hitsMap)) {
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
            if (sourceObject instanceof Map<?, ?> source && source.get("id") != null) {
                ids.add(String.valueOf(source.get("id")));
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

    private void refreshIndex() {
        try {
            ensureIndex();
            client.post()
                    .uri("/{index}/_refresh", indexName)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException error) {
            logger.warn("Drug Elasticsearch refresh failed: {}", error.getMessage());
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("drugCode", Map.of("type", "keyword", "fields", Map.of("text", Map.of("type", "text"))));
        properties.put("drugName", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))));
        properties.put("specification", Map.of("type", "text"));
        properties.put("unit", Map.of("type", "keyword"));
        properties.put("unitPrice", Map.of("type", "scaled_float", "scaling_factor", 100));
        properties.put("dosageForm", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))));
        properties.put("storageCondition", Map.of("type", "keyword"));
        properties.put("quantity", Map.of("type", "integer"));
        properties.put("warningThreshold", Map.of("type", "integer"));
        properties.put("lowStock", Map.of("type", "boolean"));
        return Map.of("mappings", Map.of("properties", properties));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
