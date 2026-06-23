package com.cloudbrain.cashier.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrescriptionPaymentClient {
    private final RestClient client;
    private final String key;

    public PrescriptionPaymentClient(
            @Value("${services.pharmacy.base-url:http://localhost:8107}") String url,
            @Value("${internal.api-key}") String key) {
        this.client = RestClient.builder().baseUrl(url).build();
        this.key = key;
    }

    public void confirm(String id, String patientId, String paymentOrderId) {
        client.post()
                .uri("/api/internal/prescriptions/{id}/payment-confirmation", id)
                .header("X-Internal-Api-Key", key)
                .body(Map.of("patientId", patientId, "paymentOrderId", paymentOrderId))
                .retrieve()
                .toBodilessEntity();
    }
}
