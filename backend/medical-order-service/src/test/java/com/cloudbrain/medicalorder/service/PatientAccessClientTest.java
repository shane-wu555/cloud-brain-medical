package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class PatientAccessClientTest {
    @Test
    void ownsAndBoundPatientIdReadInternalApis() {
        PatientAccessClient client = new PatientAccessClient("http://patient", "internal-key");
        RestClient restClient = Mockito.mock(RestClient.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec getSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);
        ReflectionTestUtils.setField(client, "client", restClient);
        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(getSpec.uri("/api/internal/patients/accounts/{accountId}/binding", "account-1")).thenReturn(headersSpec);
        when(headersSpec.header("X-Internal-Api-Key", "internal-key")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Map.of("owned", true))
                .thenReturn(Map.of("hasBoundPatient", true, "boundPatientId", "patient-1"))
                .thenReturn(Map.of("hasBoundPatient", false));

        assertThat(client.owns("account-1", "patient-1")).isTrue();
        assertThat(client.boundPatientId("account-1")).isEqualTo("patient-1");
        assertThat(client.boundPatientId("account-1")).isNull();
    }

    @Test
    void ownsReturnsFalseAndBoundPatientIdHandlesNullPayloads() {
        PatientAccessClient client = new PatientAccessClient("http://patient", "internal-key");
        RestClient restClient = Mockito.mock(RestClient.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec getSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);
        ReflectionTestUtils.setField(client, "client", restClient);
        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(getSpec.uri("/api/internal/patients/accounts/{accountId}/binding", "account-2")).thenReturn(headersSpec);
        when(headersSpec.header("X-Internal-Api-Key", "internal-key")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        Map<String, Object> payloadWithNullId = new HashMap<>();
        payloadWithNullId.put("hasBoundPatient", true);
        payloadWithNullId.put("boundPatientId", null);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(null)
                .thenReturn(Map.of("owned", false))
                .thenReturn(payloadWithNullId);

        assertThat(client.owns("account-2", "patient-2")).isFalse();
        assertThat(client.owns("account-2", "patient-2")).isFalse();
        assertThat(client.boundPatientId("account-2")).isNull();
    }
}
