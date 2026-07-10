package com.cloudbrain.medicalrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PatientAccessClientTest {
    @Mock
    RestClient client;

    @Mock
    @SuppressWarnings("rawtypes")
    RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    @Test
    void ownsReturnsTrueWhenResponseContainsOwnedFlag() {
        PatientAccessClient accessClient = client();
        doReturn(requestHeadersUriSpec).when(client).get();
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.header("X-Internal-Api-Key", "internal-key")).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Boolean>>>any()))
                .thenReturn(Map.of("owned", true));

        assertThat(accessClient.owns("account-1", "patient-1")).isTrue();
    }

    @Test
    void boundPatientIdReturnsNullWhenBindingMissing() {
        PatientAccessClient accessClient = client();
        doReturn(requestHeadersUriSpec).when(client).get();
        when(requestHeadersUriSpec.uri("/api/internal/patients/accounts/{accountId}/binding", "account-1"))
                .thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.header("X-Internal-Api-Key", "internal-key")).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(Map.of("hasBoundPatient", false));

        assertThat(accessClient.boundPatientId("account-1")).isNull();

        when(responseSpec.body(org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(Map.of("hasBoundPatient", true, "boundPatientId", "patient-1"));

        assertThat(accessClient.boundPatientId("account-1")).isEqualTo("patient-1");
    }

    private PatientAccessClient client() {
        PatientAccessClient accessClient = new PatientAccessClient("http://localhost:8103", "internal-key");
        ReflectionTestUtils.setField(accessClient, "client", client);
        return accessClient;
    }
}
