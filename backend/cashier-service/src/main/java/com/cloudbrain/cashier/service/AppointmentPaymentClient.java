package com.cloudbrain.cashier.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppointmentPaymentClient {
    private final RestClient client;
    private final String internalApiKey;
    public AppointmentPaymentClient(@Value("${services.appointment.base-url}") String url,
            @Value("${internal.api-key}") String internalApiKey) {
        this.client=RestClient.builder().baseUrl(url).build(); this.internalApiKey=internalApiKey;
    }
    public void confirm(String appointmentId,String patientId,String paymentMethod,String paymentOrderId) {
        client.post().uri("/api/internal/appointments/{id}/payment-confirmation",appointmentId)
                .header("X-Internal-Api-Key",internalApiKey)
                .body(Map.of("patientId",patientId,"paymentMethod",paymentMethod,"paymentOrderId",paymentOrderId))
                .retrieve().toBodilessEntity();
    }
    public void fail(String appointmentId,String patientId,String paymentOrderId) {
        client.post().uri("/api/internal/appointments/{id}/payment-failure",appointmentId)
                .header("X-Internal-Api-Key",internalApiKey)
                .body(Map.of("patientId",patientId,"paymentOrderId",paymentOrderId))
                .retrieve().toBodilessEntity();
    }
    public void refund(String appointmentId, String patientId, String operatorId) {
        client.post().uri("/api/internal/appointments/{id}/refund-confirmation", appointmentId)
                .header("X-Internal-Api-Key", internalApiKey)
                .body(Map.of("patientId", patientId, "operatorId", operatorId))
                .retrieve().toBodilessEntity();
    }
}
