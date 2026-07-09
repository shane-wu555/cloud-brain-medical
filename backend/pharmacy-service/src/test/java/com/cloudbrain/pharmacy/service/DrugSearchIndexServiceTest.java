package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DrugSearchIndexServiceTest {
    @Test
    void indexAndSearchReturnEmptyWhenDisabledOrInputInvalid() {
        DrugSearchIndexService disabled = new DrugSearchIndexService(false, "http://localhost:9200", "drug-index");
        DrugSearchIndexService enabled = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");

        assertThat(disabled.index(drug())).isFalse();
        assertThat(enabled.index(null)).isFalse();
        assertThat(enabled.index(new PharmacyRepository.Drug(" ", "DRUG-1", "Aspirin", "100mg", "box", BigDecimal.TEN, "tablet", "ROOM", 10, 5))).isFalse();
        assertThat(disabled.reindex(List.of(drug()))).isZero();
        assertThat(enabled.reindex(List.of())).isZero();
        assertThat(disabled.searchDrugIds("aspirin", "ROOM", 10)).isEmpty();
        assertThat(enabled.searchDrugIds(" ", "ROOM", 10)).isEmpty();
    }

    @Test
    void documentIncludesLowStockMarker() throws Exception {
        DrugSearchIndexService service = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) invoke(service, "document", new Class<?>[] {PharmacyRepository.Drug.class}, drug());

        assertThat(document).containsEntry("id", "drug-1");
        assertThat(document).containsEntry("drugCode", "DRUG-1");
        assertThat(document).containsEntry("lowStock", false);
    }

    @Test
    void searchBodyIncludesStorageFilterAndNormalizedLimit() throws Exception {
        DrugSearchIndexService service = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) invoke(service, "searchBody", new Class<?>[] {String.class, String.class, int.class}, " aspirin ", " ROOM ", 500);

        assertThat(body.get("size")).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) body.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertThat((List<?>) bool.get("filter")).hasSize(1);
        assertThat((List<?>) body.get("sort")).hasSize(3);
    }

    @Test
    void readIdsPrefersSourceIdAndFallsBackToElasticDocumentId() throws Exception {
        DrugSearchIndexService service = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");
        Map<String, Object> response = Map.of(
                "hits", Map.of(
                        "hits", List.of(
                                Map.of("_source", Map.of("id", "drug-1")),
                                Map.of("_id", "drug-2"),
                                Map.of())));

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) invoke(service, "readIds", new Class<?>[] {Map.class}, response);

        assertThat(ids).containsExactly("drug-1", "drug-2");
    }

    @Test
    void indexDefinitionContainsExpectedFields() throws Exception {
        DrugSearchIndexService service = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");

        @SuppressWarnings("unchecked")
        Map<String, Object> definition = (Map<String, Object>) invoke(service, "indexDefinition", new Class<?>[0]);

        @SuppressWarnings("unchecked")
        Map<String, Object> mappings = (Map<String, Object>) definition.get("mappings");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
        assertThat(properties).containsKeys("id", "drugCode", "drugName", "storageCondition", "lowStock");
    }

    @Test
    void reflectiveInvocationSurfacesUnexpectedErrors() {
        DrugSearchIndexService service = new DrugSearchIndexService(true, "http://localhost:9200", "drug-index");

        assertThatThrownBy(() -> invoke(service, "missingMethod", new Class<?>[0]))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private PharmacyRepository.Drug drug() {
        return new PharmacyRepository.Drug(
                "drug-1",
                "DRUG-1",
                "Aspirin",
                "100mg",
                "box",
                BigDecimal.TEN,
                "tablet",
                "ROOM",
                20,
                5);
    }
}
