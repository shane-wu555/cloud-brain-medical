package com.cloudbrain.appointment.service;

import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordEventDispatcher {
    private final MedicalRecordEventRepository repository;
    private final MedicalRecordClient client;
    private final CashierClient cashierClient;
    private final ObjectMapper objectMapper;

    public MedicalRecordEventDispatcher(
            MedicalRecordEventRepository repository,
            MedicalRecordClient client,
            CashierClient cashierClient,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.client = client;
        this.cashierClient = cashierClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${integration.dispatch.fixed-delay-ms:5000}")
    public void dispatch() {
        for (MedicalRecordEventRepository.PendingEvent event : repository.findPending(20)) {
            try {
                Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {});
                switch (event.eventType()) {
                    case "APPOINTMENT_PAID" -> client.createInitialRecord(payload);
                    case "PAYMENT_COMPLETED" -> cashierClient.recordPayment(payload);
                    case "REFUND_COMPLETED" -> cashierClient.recordRefund(payload);
                    default -> throw new IllegalStateException("不支持的集成事件: " + event.eventType());
                }
                repository.markCompleted(event.id());
            } catch (Exception exception) {
                repository.markFailed(event.id(), event.retryCount(), exception.getMessage());
            }
        }
    }
}
