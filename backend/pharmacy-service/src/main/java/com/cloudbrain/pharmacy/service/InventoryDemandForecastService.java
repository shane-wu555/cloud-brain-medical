package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import com.cloudbrain.pharmacy.repository.PharmacyRepository.DrugDemandObservation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryDemandForecastService {
    private static final int MAX_THRESHOLD = 220;
    private static final Set<String> HIGH_VALUE_CODES = Set.of(
            "DRUG-INSULIN", "DRUG-EDARAVONE", "DRUG-LEVETIRAC", "DRUG-SITAGLIPTIN",
            "DRUG-TIOTROPIUM", "DRUG-IVABRADINE", "DRUG-AMLODIPINE", "DRUG-METOPROLOL",
            "DRUG-LISINOPRIL", "DRUG-ATORVAST", "DRUG-CLOPIDOGR");
    private static final Set<String> LOW_VOLATILITY_CODES = Set.of(
            "DRUG-AMLODIPINE", "DRUG-METOPROLOL", "DRUG-LISINOPRIL", "DRUG-ATORVAST",
            "DRUG-CLOPIDOGR", "DRUG-WARFARIN", "DRUG-METFORMIN", "DRUG-SITAGLIPTIN",
            "DRUG-ALENDRONATE", "DRUG-CALCIUM");
    private static final Set<String> HIGH_VOLATILITY_CODES = Set.of(
            "DRUG-INSULIN", "DRUG-MANNITOL", "DRUG-SALBUTAMOL", "DRUG-BUDESONIDE",
            "DRUG-AZITHRO", "DRUG-DEXAMETH", "DRUG-DIAZEPAM", "DRUG-VIT-C");
    private static final Map<String, Integer> CATALOG_THRESHOLDS = Map.ofEntries(
            Map.entry("DRUG-ASPIRIN", 55),
            Map.entry("DRUG-CLOPIDOGR", 48),
            Map.entry("DRUG-WARFARIN", 35),
            Map.entry("DRUG-LEVETIRAC", 42),
            Map.entry("DRUG-VALPROATE", 40),
            Map.entry("DRUG-MANNITOL", 60),
            Map.entry("DRUG-EDARAVONE", 38),
            Map.entry("DRUG-ATORVAST", 50),
            Map.entry("DRUG-METOPROLOL", 52),
            Map.entry("DRUG-AMLODIPINE", 55),
            Map.entry("DRUG-LISINOPRIL", 48),
            Map.entry("DRUG-IVABRADINE", 32),
            Map.entry("DRUG-PANTOPRAZ", 50),
            Map.entry("DRUG-OMEPRAZOLE", 55),
            Map.entry("DRUG-DOMPERIDON", 45),
            Map.entry("DRUG-BISMUTH", 40),
            Map.entry("DRUG-LACTULOSE", 36),
            Map.entry("DRUG-INSULIN", 75),
            Map.entry("DRUG-METFORMIN", 60),
            Map.entry("DRUG-GLIPIZIDE", 42),
            Map.entry("DRUG-SITAGLIPTIN", 32),
            Map.entry("DRUG-SALBUTAMOL", 70),
            Map.entry("DRUG-TIOTROPIUM", 28),
            Map.entry("DRUG-BUDESONIDE", 55),
            Map.entry("DRUG-AZITHRO", 65),
            Map.entry("DRUG-CELECOXIB", 42),
            Map.entry("DRUG-CALCIUM", 38),
            Map.entry("DRUG-ALENDRONATE", 30),
            Map.entry("DRUG-DEXAMETH", 65),
            Map.entry("DRUG-DIAZEPAM", 35),
            Map.entry("DRUG-VITAMIN-B12", 40),
            Map.entry("DRUG-FOLIC-ACID", 35),
            Map.entry("DRUG-VIT-C", 60));

    private final PharmacyRepository repository;
    private final int defaultLookbackDays;
    private final int leadTimeDays;
    private final int minTrainingSampleDays;

    public InventoryDemandForecastService(
            PharmacyRepository repository,
            @Value("${inventory.ai-forecast.lookback-days:90}") int defaultLookbackDays,
            @Value("${inventory.ai-forecast.lead-time-days:14}") int leadTimeDays,
            @Value("${inventory.ai-forecast.min-training-sample-days:30}") int minTrainingSampleDays) {
        this.repository = repository;
        this.defaultLookbackDays = Math.max(7, defaultLookbackDays);
        this.leadTimeDays = Math.max(1, leadTimeDays);
        this.minTrainingSampleDays = Math.max(7, minTrainingSampleDays);
    }

    public ForecastRun preview(int lookbackDays) {
        return forecast(Math.max(7, lookbackDays), 0);
    }

    @Transactional
    public ForecastRun refreshDynamicThresholds() {
        ForecastRun run = forecast(defaultLookbackDays, 0);
        int updated = 0;
        for (InventoryForecast item : run.items()) {
            if (item.recommendedWarningThreshold() != item.currentWarningThreshold()) {
                updated += repository.updateWarningThreshold(item.drugId(), item.recommendedWarningThreshold());
            }
        }
        return new ForecastRun(run.lookbackDays(), run.leadTimeDays(), updated, run.items());
    }

    private ForecastRun forecast(int lookbackDays, int updatedCount) {
        List<DrugDemandObservation> observations = repository.drugDemandObservations(lookbackDays);
        Map<String, List<DrugDemandObservation>> byDrug = observations.stream()
                .collect(Collectors.groupingBy(DrugDemandObservation::drugId, LinkedHashMap::new, Collectors.toList()));
        List<InventoryForecast> items = byDrug.values().stream()
                .map(this::forecastDrug)
                .toList();
        return new ForecastRun(lookbackDays, leadTimeDays, updatedCount, items);
    }

    private InventoryForecast forecastDrug(List<DrugDemandObservation> rows) {
        DrugDemandObservation first = rows.get(0);
        int[] series = rows.stream().mapToInt(DrugDemandObservation::dispensedQuantity).toArray();
        int sampleDays = series.length;
        int nonZeroDemandDays = (int) rows.stream().filter(row -> row.dispensedQuantity() > 0).count();
        int totalDemand = rows.stream().mapToInt(DrugDemandObservation::dispensedQuantity).sum();
        double averageDailyDemand = sampleDays == 0 ? 0.0 : (double) totalDemand / sampleDays;
        double standardDeviation = standardDeviation(series, averageDailyDemand);
        double demandVolatility = averageDailyDemand <= 0.0 ? 0.0 : standardDeviation / averageDailyDemand;
        ValueTier valueTier = valueTier(first.drugCode(), first.unitPrice());
        VolatilityTier volatilityTier = volatilityTier(first.drugCode(), demandVolatility, nonZeroDemandDays);
        ForecastModelRoute modelRoute = modelRoute(volatilityTier);
        boolean useDemandModel = nonZeroDemandDays >= minTrainingSampleDays && totalDemand >= minTrainingSampleDays;
        double predictedDailyDemand = useDemandModel
                ? predictDailyDemand(series, averageDailyDemand, standardDeviation, modelRoute)
                : catalogDailyDemand(first.drugCode(), valueTier, volatilityTier);
        int recommendedThreshold = useDemandModel
                ? threshold(predictedDailyDemand, standardDeviation, valueTier, volatilityTier)
                : catalogThreshold(first.drugCode(), valueTier, volatilityTier);
        return new InventoryForecast(
                first.drugId(),
                first.drugCode(),
                first.drugName(),
                first.unitPrice(),
                first.quantity(),
                first.warningThreshold(),
                recommendedThreshold,
                first.quantity() <= recommendedThreshold,
                sampleDays,
                nonZeroDemandDays,
                totalDemand,
                round(averageDailyDemand),
                round(demandVolatility),
                round(predictedDailyDemand),
                valueTier,
                volatilityTier,
                modelRoute);
    }

    private ValueTier valueTier(String code, BigDecimal unitPrice) {
        if (HIGH_VALUE_CODES.contains(code)) return ValueTier.HIGH;
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.valueOf(60)) >= 0) return ValueTier.HIGH;
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.valueOf(20)) >= 0) return ValueTier.MEDIUM;
        return ValueTier.LOW;
    }

    private VolatilityTier volatilityTier(String code, double coefficientOfVariation, int nonZeroDemandDays) {
        if (nonZeroDemandDays >= minTrainingSampleDays) {
            if (coefficientOfVariation >= 0.75) return VolatilityTier.HIGH;
            if (coefficientOfVariation >= 0.35) return VolatilityTier.MEDIUM;
            return VolatilityTier.LOW;
        }
        if (HIGH_VOLATILITY_CODES.contains(code)) return VolatilityTier.HIGH;
        if (LOW_VOLATILITY_CODES.contains(code)) return VolatilityTier.LOW;
        return VolatilityTier.MEDIUM;
    }

    private ForecastModelRoute modelRoute(VolatilityTier volatilityTier) {
        return switch (volatilityTier) {
            case HIGH -> ForecastModelRoute.ENSEMBLE_XGBOOST_PROPHET;
            case MEDIUM -> ForecastModelRoute.LIGHTWEIGHT_TREND;
            case LOW -> ForecastModelRoute.RULE_BASED;
        };
    }

    private double predictDailyDemand(int[] series, double averageDailyDemand, double standardDeviation,
            ForecastModelRoute modelRoute) {
        return switch (modelRoute) {
            case ENSEMBLE_XGBOOST_PROPHET -> ensembleDemand(series, averageDailyDemand, standardDeviation);
            case LIGHTWEIGHT_TREND -> lightweightTrendDemand(series, averageDailyDemand);
            case RULE_BASED -> ruleBasedDemand(series, averageDailyDemand);
        };
    }

    private double ensembleDemand(int[] series, double averageDailyDemand, double standardDeviation) {
        double prophetLike = prophetLikeForecast(series, averageDailyDemand);
        double xgboostLike = xgboostLikeForecast(series, averageDailyDemand, standardDeviation);
        return Math.max(0.1, prophetLike * 0.45 + xgboostLike * 0.45 + averageDailyDemand * 0.10);
    }

    private double prophetLikeForecast(int[] series, double averageDailyDemand) {
        double trend = linearTrendPerDay(series) * leadTimeDays;
        double seasonality = weeklySeasonality(series);
        return Math.max(0.1, averageDailyDemand + trend + seasonality);
    }

    private double xgboostLikeForecast(int[] series, double averageDailyDemand, double standardDeviation) {
        double last7 = trailingAverage(series, 7);
        double last28 = trailingAverage(series, 28);
        double spikeScore = standardDeviation * 0.35;
        return Math.max(0.1, last7 * 0.45 + last28 * 0.35 + averageDailyDemand * 0.20 + spikeScore);
    }

    private double lightweightTrendDemand(int[] series, double averageDailyDemand) {
        double last14 = trailingAverage(series, 14);
        double last28 = trailingAverage(series, 28);
        return Math.max(0.1, last14 * 0.50 + last28 * 0.30 + averageDailyDemand * 0.20);
    }

    private double ruleBasedDemand(int[] series, double averageDailyDemand) {
        return Math.max(0.1, Math.max(averageDailyDemand, trailingAverage(series, 14)));
    }

    private int threshold(double predictedDailyDemand, double standardDeviation, ValueTier valueTier,
            VolatilityTier volatilityTier) {
        double serviceLevel = switch (volatilityTier) {
            case HIGH -> 1.65;
            case MEDIUM -> 1.35;
            case LOW -> 1.15;
        };
        double safetyStock = standardDeviation * Math.sqrt(leadTimeDays) * serviceLevel;
        int raw = (int) Math.ceil(predictedDailyDemand * leadTimeDays * serviceLevel + safetyStock);
        return clamp(raw, minimumThreshold(valueTier, volatilityTier), MAX_THRESHOLD);
    }

    private int catalogThreshold(String code, ValueTier valueTier, VolatilityTier volatilityTier) {
        Integer configured = CATALOG_THRESHOLDS.get(code);
        if (configured != null) return configured;
        return threshold(catalogDailyDemand(code, valueTier, volatilityTier), 0.0, valueTier, volatilityTier);
    }

    private double catalogDailyDemand(String code, ValueTier valueTier, VolatilityTier volatilityTier) {
        Integer configured = CATALOG_THRESHOLDS.get(code);
        if (configured != null) {
            double serviceLevel = switch (volatilityTier) {
                case HIGH -> 1.65;
                case MEDIUM -> 1.35;
                case LOW -> 1.15;
            };
            return Math.max(0.1, configured / (leadTimeDays * serviceLevel));
        }
        double base = switch (valueTier) {
            case HIGH -> 2.8;
            case MEDIUM -> 2.4;
            case LOW -> 2.0;
        };
        return switch (volatilityTier) {
            case HIGH -> base + 1.1;
            case MEDIUM -> base + 0.5;
            case LOW -> base;
        };
    }

    private int minimumThreshold(ValueTier valueTier, VolatilityTier volatilityTier) {
        return switch (volatilityTier) {
            case HIGH -> valueTier == ValueTier.HIGH ? 55 : 45;
            case MEDIUM -> valueTier == ValueTier.HIGH ? 35 : 28;
            case LOW -> valueTier == ValueTier.HIGH ? 28 : 20;
        };
    }

    private double standardDeviation(int[] values, double average) {
        if (values.length == 0) return 0.0;
        double variance = 0.0;
        for (int value : values) {
            variance += Math.pow(value - average, 2);
        }
        return Math.sqrt(variance / values.length);
    }

    private double trailingAverage(int[] values, int days) {
        if (values.length == 0) return 0.0;
        int start = Math.max(0, values.length - days);
        int count = values.length - start;
        int sum = 0;
        for (int index = start; index < values.length; index += 1) {
            sum += values[index];
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }

    private double linearTrendPerDay(int[] values) {
        if (values.length < 14) return 0.0;
        int midpoint = values.length / 2;
        double first = average(values, 0, midpoint);
        double second = average(values, midpoint, values.length);
        return (second - first) / Math.max(1, values.length - midpoint);
    }

    private double weeklySeasonality(int[] values) {
        if (values.length < 14) return 0.0;
        int targetDay = (values.length - 1) % 7;
        List<Integer> sameWeekday = new ArrayList<>();
        for (int index = targetDay; index < values.length; index += 7) {
            sameWeekday.add(values[index]);
        }
        DoubleSummaryStatistics stats = sameWeekday.stream().mapToDouble(Integer::doubleValue).summaryStatistics();
        return stats.getCount() == 0 ? 0.0 : stats.getAverage() - average(values, 0, values.length);
    }

    private double average(int[] values, int startInclusive, int endExclusive) {
        if (endExclusive <= startInclusive) return 0.0;
        int sum = 0;
        for (int index = startInclusive; index < endExclusive; index += 1) {
            sum += values[index];
        }
        return (double) sum / (endExclusive - startInclusive);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public enum ValueTier {
        HIGH, MEDIUM, LOW
    }

    public enum VolatilityTier {
        HIGH, MEDIUM, LOW
    }

    public enum ForecastModelRoute {
        ENSEMBLE_XGBOOST_PROPHET, LIGHTWEIGHT_TREND, RULE_BASED
    }

    public record ForecastRun(
            int lookbackDays,
            int leadTimeDays,
            int updatedCount,
            List<InventoryForecast> items) {
    }

    public record InventoryForecast(
            String drugId,
            String drugCode,
            String drugName,
            BigDecimal unitPrice,
            int quantity,
            int currentWarningThreshold,
            int recommendedWarningThreshold,
            boolean lowStock,
            int sampleDays,
            int nonZeroDemandDays,
            int totalDemand,
            double averageDailyDemand,
            double demandVolatility,
            double predictedDailyDemand,
            ValueTier valueTier,
            VolatilityTier volatilityTier,
            ForecastModelRoute modelRoute) {
    }
}
