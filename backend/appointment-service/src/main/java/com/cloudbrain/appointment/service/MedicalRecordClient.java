package com.cloudbrain.appointment.service;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MedicalRecordClient {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8105")
            .build();

    public void createInitialRecord(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/medical-records/initial")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
