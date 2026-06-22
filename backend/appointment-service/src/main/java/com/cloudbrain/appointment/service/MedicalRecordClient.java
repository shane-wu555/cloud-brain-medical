package com.cloudbrain.appointment.service;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

@Component
public class MedicalRecordClient {
    private final RestClient restClient;
    private final String internalApiKey;
    public MedicalRecordClient(@Value("${services.medical-record.base-url:http://localhost:8105}") String url,
            @Value("${internal.api-key}") String internalApiKey) {
        this.restClient=RestClient.builder().baseUrl(url).build(); this.internalApiKey=internalApiKey;
    }

    public void createInitialRecord(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/medical-records/initial")
                .header("X-Internal-Api-Key",internalApiKey)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public boolean isSaved(String appointmentId) {
        Map<String,Boolean> result=restClient.get().uri("/api/medical-records/internal/{id}/saved",appointmentId)
                .header("X-Internal-Api-Key",internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<Map<String,Boolean>>(){});
        return result!=null&&Boolean.TRUE.equals(result.get("saved"));
    }
}
