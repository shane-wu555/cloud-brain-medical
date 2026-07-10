package com.cloudbrain.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleInsightServiceBranchTest {
    @Test
    void reflectionHelpersCoverNullParsingAndSummaryFallbacks() throws Exception {
        ScheduleInsightService service = new ScheduleInsightService("http://localhost:8104", "internal-key", true);

        ScheduleInsightService.ScheduleInsight empty = (ScheduleInsightService.ScheduleInsight) invoke(
                service, "mapInsight", new Class<?>[] {Map.class}, new Object[] {null});
        assertThat(empty).isEqualTo(ScheduleInsightService.ScheduleInsight.empty());

        ScheduleInsightService.ScheduleInsight notReady = (ScheduleInsightService.ScheduleInsight) invoke(
                service,
                "mapInsight",
                new Class<?>[] {Map.class},
                Map.of("sampleSize", 250, "trainingReady", false));
        assertThat(notReady.trainingReady()).isFalse();
        assertThat(notReady.summary()).isEmpty();

        assertThat(invoke(service, "averageOf", new Class<?>[] {Map.class, List.class}, Map.of(), List.of(1, 2, 3)))
                .isEqualTo(0);
        assertThat(invoke(service, "intValue", new Class<?>[] {Object.class, int.class}, "bad", 3)).isEqualTo(3);
        assertThat(invoke(service, "objectList", new Class<?>[] {Object.class}, "not-a-list")).isEqualTo(List.of());
        assertThat(invoke(service, "stringValue", new Class<?>[] {Object.class}, new Object[] {null})).isNull();
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
