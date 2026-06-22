package com.cloudbrain.appointment.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CashierClient {
    private final RestClient client;
    private final String internalApiKey;

    public CashierClient(
            @Value("${services.cashier.base-url:http://localhost:8113}") String baseUrl,
            @Value("${internal.api-key}") String internalApiKey) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void recordPayment(Map<String, Object> payload) { post("/api/internal/payments", payload); }
    public void recordRefund(Map<String, Object> payload) { post("/api/internal/refunds", payload); }

    private void post(String uri, Map<String, Object> payload) {
        client.post().uri(uri).header("X-Internal-Api-Key", internalApiKey).body(payload).retrieve().toBodilessEntity();
    }
}
