package com.cloudbrain.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class ScheduleInsightServiceTest {
    @Mock
    RestClient appointmentClient;

    @Mock
    @SuppressWarnings("rawtypes")
    RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    @Test
    void refreshKeepsInsightEmptyWhenDisabled() {
        ScheduleInsightService service = new ScheduleInsightService("http://localhost:8104", "internal-key", false);

        service.refresh();

        assertThat(service.current()).isEqualTo(ScheduleInsightService.ScheduleInsight.empty());
    }

    @Test
    void refreshMapsTrainingReadyInsight() {
        ScheduleInsightService service = serviceWithClient(true);
        Map<String, Object> response = Map.of(
                "sampleSize", 260,
                "trainingReady", true,
                "doctorAverages", List.of(
                        Map.of("doctorId", "doctor-1", "averageVisits", 14),
                        Map.of("doctorId", "doctor-1", "averageVisits", 10),
                        Map.of("doctorId", "doctor-2", "averageVisits", "18")),
                "departmentAverages", List.of(Map.of("departmentId", "dept-1", "averageVisits", 22)),
                "weekdayAverages", List.of(
                        Map.of("isoDow", 1, "averageVisits", 25),
                        Map.of("isoDow", 6, "averageVisits", 10)));
        doReturn(requestHeadersUriSpec).when(appointmentClient).get();
        when(requestHeadersUriSpec.uri("/api/internal/appointments/scheduling-history-summary?lookbackDays=90"))
                .thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.header("X-Internal-Api-Key", "internal-key"))
                .thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(response);

        service.refresh();

        ScheduleInsightService.ScheduleInsight insight = service.current();
        assertThat(insight.trainingReady()).isTrue();
        assertThat(insight.doctorAverageVisits()).containsEntry("doctor-1", 14).containsEntry("doctor-2", 18);
        assertThat(insight.departmentAverageVisits()).containsEntry("dept-1", 22);
        assertThat(insight.weekdayAverageVisits()).containsEntry(1, 25).containsEntry(6, 10);
        assertThat(insight.summary()).isNotBlank();
    }

    @Test
    void refreshDropsInsightWhenSampleTooSmallOrRemoteFails() {
        ScheduleInsightService service = serviceWithClient(true);
        doReturn(requestHeadersUriSpec).when(appointmentClient).get();
        when(requestHeadersUriSpec.uri("/api/internal/appointments/scheduling-history-summary?lookbackDays=90"))
                .thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.header("X-Internal-Api-Key", "internal-key"))
                .thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(Map.of("sampleSize", 100, "trainingReady", true));

        service.refresh();
        assertThat(service.current().trainingReady()).isFalse();

        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenThrow(new RestClientException("down"));
        service.refresh();

        assertThat(service.current()).isEqualTo(ScheduleInsightService.ScheduleInsight.empty());
    }

    private ScheduleInsightService serviceWithClient(boolean enabled) {
        ScheduleInsightService service = new ScheduleInsightService("http://localhost:8104", "internal-key", enabled);
        ReflectionTestUtils.setField(service, "appointmentClient", appointmentClient);
        return service;
    }
}
