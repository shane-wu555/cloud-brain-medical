package com.cloudbrain.appointment.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestClient client;
    private final String internalApiKey;

    public NotificationClient(
            @Value("${services.patient.base-url}") String url,
            @Value("${internal.api-key}") String key) {
        this.client = RestClient.builder().baseUrl(url).build();
        this.internalApiKey = key;
    }

    public void notify(String patientId, String category, String title, String body,
                       String referenceType, String referenceId) {
        try {
            client.post()
                    .uri("/api/internal/patients/notifications")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(Map.of(
                            "patientId", patientId,
                            "category", category,
                            "title", title,
                            "body", body == null ? "" : body,
                            "referenceType", referenceType,
                            "referenceId", referenceId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to create notification for patient {}: {}", patientId, e.getMessage());
        }
    }
}
