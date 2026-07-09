package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void previewClampsLookbackToMinimumSevenDays() {
        when(repository.drugDemandObservations(7)).thenReturn(observations(
                "drug-amlodipine", "DRUG-AMLODIPINE", "amlodipine", BigDecimal.TEN, 100, 33, 7, 2));

        InventoryDemandForecastService.ForecastRun run = service.preview(1);

        assertThat(run.lookbackDays()).isEqualTo(7);
        verify(repository).drugDemandObservations(7);
    }

    @Test
    void stableDemandUsesRuleBasedRouteAndSkipsThresholdUpdateWhenAlreadyAligned() {
        when(repository.drugDemandObservations(90)).thenReturn(observationsFromSeries(
                "drug-amlodipine",
                "DRUG-AMLODIPINE",
                "amlodipine",
                BigDecimal.TEN,
                100,
                33,
                constantSeries(30, 2)));

        InventoryDemandForecastService.ForecastRun run = service.refreshDynamicThresholds();

        assertThat(run.updatedCount()).isZero();
        assertThat(run.items()).singleElement().satisfies(item -> {
            assertThat(item.valueTier()).isEqualTo(InventoryDemandForecastService.ValueTier.HIGH);
            assertThat(item.volatilityTier()).isEqualTo(InventoryDemandForecastService.VolatilityTier.LOW);
            assertThat(item.modelRoute()).isEqualTo(InventoryDemandForecastService.ForecastModelRoute.RULE_BASED);
            assertThat(item.recommendedWarningThreshold()).isEqualTo(33);
            assertThat(item.lowStock()).isFalse();
        });
        verify(repository, never()).updateWarningThreshold("drug-amlodipine", 33);
    }

    @Test
    void moderateDemandVariationUsesLightweightTrendRoute() {
        when(repository.drugDemandObservations(90)).thenReturn(observationsFromSeries(
                "drug-pantopraz",
                "DRUG-PANTOPRAZ",
                "pantopraz",
                BigDecimal.valueOf(30),
                80,
                25,
                alternatingSeries(30, 1, 3)));

        InventoryDemandForecastService.ForecastRun run = service.preview(90);

        assertThat(run.items()).singleElement().satisfies(item -> {
            assertThat(item.valueTier()).isEqualTo(InventoryDemandForecastService.ValueTier.MEDIUM);
            assertThat(item.volatilityTier()).isEqualTo(InventoryDemandForecastService.VolatilityTier.MEDIUM);
            assertThat(item.modelRoute()).isEqualTo(InventoryDemandForecastService.ForecastModelRoute.LIGHTWEIGHT_TREND);
            assertThat(item.recommendedWarningThreshold()).isGreaterThan(25);
        });
    }

    @Test
    void highlyVariableDemandUsesEnsembleRouteAndFlagsLowStock() {
        when(repository.drugDemandObservations(90)).thenReturn(observationsFromSeries(
                "drug-valproate",
                "DRUG-VALPROATE",
                "valproate",
                BigDecimal.valueOf(15),
                20,
                10,
                alternatingSeries(30, 1, 7)));

        InventoryDemandForecastService.ForecastRun run = service.preview(90);

        assertThat(run.items()).singleElement().satisfies(item -> {
            assertThat(item.volatilityTier()).isEqualTo(InventoryDemandForecastService.VolatilityTier.HIGH);
            assertThat(item.modelRoute()).isEqualTo(InventoryDemandForecastService.ForecastModelRoute.ENSEMBLE_XGBOOST_PROPHET);
            assertThat(item.predictedDailyDemand()).isGreaterThan(0.0);
            assertThat(item.lowStock()).isTrue();
        });
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

    private List<DrugDemandObservation> observationsFromSeries(String drugId, String drugCode, String drugName,
            BigDecimal unitPrice, int quantity, int warningThreshold, int[] series) {
        List<DrugDemandObservation> rows = new ArrayList<>();
        LocalDate firstDay = LocalDate.now().minusDays(series.length - 1L);
        for (int index = 0; index < series.length; index += 1) {
            rows.add(new DrugDemandObservation(drugId, drugCode, drugName, unitPrice, quantity, warningThreshold,
                    firstDay.plusDays(index), series[index]));
        }
        return rows;
    }

    private int[] constantSeries(int days, int value) {
        int[] series = new int[days];
        java.util.Arrays.fill(series, value);
        return series;
    }

    private int[] alternatingSeries(int days, int first, int second) {
        int[] series = new int[days];
        for (int index = 0; index < days; index += 1) {
            series[index] = index % 2 == 0 ? first : second;
        }
        return series;
    }
}
