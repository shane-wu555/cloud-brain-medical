package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DrugSearchIndexBootstrapTest {
    @Mock
    PharmacyRepository repository;

    @Mock
    DrugSearchIndexService searchIndexService;

    @Test
    void refreshDrugIndexReindexesRepositoryResults() {
        List<PharmacyRepository.Drug> drugs = List.of(drug());
        when(repository.drugs(null, null)).thenReturn(drugs);
        when(searchIndexService.reindex(drugs)).thenReturn(1);
        DrugSearchIndexBootstrap bootstrap = new DrugSearchIndexBootstrap(repository, searchIndexService);

        bootstrap.refreshDrugIndex();

        verify(searchIndexService).reindex(drugs);
    }

    @Test
    void refreshDrugIndexSwallowsIndexFailures() {
        List<PharmacyRepository.Drug> drugs = List.of(drug());
        when(repository.drugs(null, null)).thenReturn(drugs);
        when(searchIndexService.reindex(drugs)).thenThrow(new IllegalStateException("boom"));
        DrugSearchIndexBootstrap bootstrap = new DrugSearchIndexBootstrap(repository, searchIndexService);

        assertThatCode(bootstrap::refreshDrugIndex).doesNotThrowAnyException();
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
                50,
                10);
    }
}
