package com.cloudbrain.doctor.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ScheduleInsightService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleInsightService.class);
    private static final int MIN_TRAINING_SAMPLE = 200;
    private final RestClient appointmentClient;
    private final String internalApiKey;
    private final boolean enabled;
    private volatile ScheduleInsight insight = ScheduleInsight.empty();

    public ScheduleInsightService(
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl,
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${scheduling.insight-enabled:false}") boolean enabled) {
        this.appointmentClient = RestClient.builder().baseUrl(appointmentUrl).build();
        this.internalApiKey = internalApiKey;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${scheduling.insight-refresh-ms:3600000}",
            initialDelayString = "${scheduling.insight-initial-delay-ms:5000}")
    public void refresh() {
        if (!enabled) {
            insight = ScheduleInsight.empty();
            return;
        }
        try {
            Map<String, Object> response = appointmentClient.get()
                    .uri("/api/internal/appointments/scheduling-history-summary?lookbackDays=90")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            insight = mapInsight(response);
            log.info("AI schedule insight refreshed: sampleSize={}, trainingReady={}, doctors={}",
                    insight.sampleSize(), insight.trainingReady(), insight.doctorAverageVisits().size());
        } catch (RestClientException exception) {
            log.warn("AI schedule insight refresh failed, using rule fallback: {}", exception.getMessage());
            insight = ScheduleInsight.empty();
        }
    }

    public ScheduleInsight current() {
        return insight;
    }

    private ScheduleInsight mapInsight(Map<String, Object> response) {
        if (response == null) return ScheduleInsight.empty();
        int sampleSize = intValue(response.get("sampleSize"), 0);
        boolean trainingReady = Boolean.TRUE.equals(response.get("trainingReady"));
        if (sampleSize < MIN_TRAINING_SAMPLE || !trainingReady) {
            return new ScheduleInsight(sampleSize, false, Map.of(), Map.of(), Map.of(), "");
        }
        Map<String, Integer> doctorAverages = objectList(response.get("doctorAverages")).stream()
                .filter(item -> stringValue(item.get("doctorId")) != null)
                .collect(Collectors.toMap(
                        item -> stringValue(item.get("doctorId")),
                        item -> intValue(item.get("averageVisits"), 0),
                        Math::max,
                        LinkedHashMap::new));
        Map<String, Integer> departmentAverages = objectList(response.get("departmentAverages")).stream()
                .filter(item -> stringValue(item.get("departmentId")) != null)
                .collect(Collectors.toMap(
                        item -> stringValue(item.get("departmentId")),
                        item -> intValue(item.get("averageVisits"), 0),
                        Math::max,
                        LinkedHashMap::new));
        Map<Integer, Integer> weekdayAverages = objectList(response.get("weekdayAverages")).stream()
                .collect(Collectors.toMap(
                        item -> intValue(item.get("isoDow"), 0),
                        item -> intValue(item.get("averageVisits"), 0),
                        Math::max,
                        LinkedHashMap::new));
        return new ScheduleInsight(sampleSize, trainingReady, doctorAverages, departmentAverages, weekdayAverages,
                buildSummary(sampleSize, trainingReady, weekdayAverages));
    }

    private String buildSummary(int sampleSize, boolean trainingReady, Map<Integer, Integer> weekdayAverages) {
        int weekdayAverage = averageOf(weekdayAverages, List.of(1, 2, 3, 4, 5));
        int weekendAverage = averageOf(weekdayAverages, List.of(6, 7));
        return "已定期调用挂号历史 API 生成排班背景摘要；近 90 天有效样本 " + sampleSize
                + " 条，工作日平均挂号量约 " + weekdayAverage
                + "，周末平均挂号量约 " + weekendAverage
                + "，排班优先保障工作日号源并兼顾医生负载均衡。";
    }

    private int averageOf(Map<Integer, Integer> values, List<Integer> keys) {
        List<Integer> present = keys.stream().map(values::get).filter(value -> value != null && value > 0).toList();
        if (present.isEmpty()) return 0;
        return Math.round((float) present.stream().mapToInt(Integer::intValue).sum() / present.size());
    }

    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<?, ?> raw = (Map<?, ?>) item;
                    Map<String, Object> mapped = new LinkedHashMap<>();
                    raw.forEach((key, itemValue) -> mapped.put(String.valueOf(key), itemValue));
                    return mapped;
                })
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) return number.intValue();
        return Optional.ofNullable(value)
                .map(String::valueOf)
                .flatMap(text -> {
                    try {
                        return Optional.of(Integer.parseInt(text));
                    } catch (NumberFormatException exception) {
                        return Optional.empty();
                    }
                })
                .orElse(defaultValue);
    }

    public record ScheduleInsight(
            int sampleSize,
            boolean trainingReady,
            Map<String, Integer> doctorAverageVisits,
            Map<String, Integer> departmentAverageVisits,
            Map<Integer, Integer> weekdayAverageVisits,
            String summary) {
        public static ScheduleInsight empty() {
            return new ScheduleInsight(
                    0,
                    false,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    "");
        }
    }
}
