package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import com.cloudbrain.pharmacy.repository.PharmacyRepository.DrugDemandObservation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryDemandForecastServiceTest {
    private final PharmacyRepository repository = mock(PharmacyRepository.class);
    private final InventoryDemandForecastService service =
            new InventoryDemandForecastService(repository, 90, 14, 30);

    @Test
    void insufficientHistoryUsesStaticCatalogThresholdAndAiRoute() {
        when(repository.drugDemandObservations(90)).thenReturn(observations(
                "drug-insulin", "DRUG-INSULIN", "insulin", BigDecimal.valueOf(35), 300, 30, 90, 0));

        InventoryDemandForecastService.ForecastRun run = service.preview(90);

        assertThat(run.items()).hasSize(1);
        InventoryDemandForecastService.InventoryForecast item = run.items().get(0);
        assertThat(item.recommendedWarningThreshold()).isEqualTo(75);
        assertThat(item.valueTier()).isEqualTo(InventoryDemandForecastService.ValueTier.HIGH);
        assertThat(item.volatilityTier()).isEqualTo(InventoryDemandForecastService.VolatilityTier.HIGH);
        assertThat(item.modelRoute()).isEqualTo(InventoryDemandForecastService.ForecastModelRoute.ENSEMBLE_XGBOOST_PROPHET);
    }

    @Test
    void refreshWritesRecommendedThresholds() {
        when(repository.drugDemandObservations(90)).thenReturn(observations(
                "drug-insulin", "DRUG-INSULIN", "insulin", BigDecimal.valueOf(35), 300, 30, 90, 0));
        when(repository.updateWarningThreshold("drug-insulin", 75)).thenReturn(1);

        InventoryDemandForecastService.ForecastRun run = service.refreshDynamicThresholds();

        assertThat(run.updatedCount()).isEqualTo(1);
        verify(repository).updateWarningThreshold("drug-insulin", 75);
    }

    private List<DrugDemandObservation> observations(String drugId, String drugCode, String drugName,
            BigDecimal unitPrice, int quantity, int warningThreshold, int days, int dailyDemand) {
        List<DrugDemandObservation> rows = new ArrayList<>();
        LocalDate firstDay = LocalDate.now().minusDays(days - 1L);
        for (int index = 0; index < days; index += 1) {
            rows.add(new DrugDemandObservation(drugId, drugCode, drugName, unitPrice, quantity, warningThreshold,
                    firstDay.plusDays(index), dailyDemand));
        }
        return rows;
    }
}
