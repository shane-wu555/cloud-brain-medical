package com.cloudbrain.audit.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cloudbrain.audit.service.AuditLogService;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {
    @Mock
    AuditLogService service;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void onMessageDeserializesPayloadAndRecordsEvent() throws Exception {
        AuditEventListener listener = new AuditEventListener(objectMapper, service);
        String payload = objectMapper.writeValueAsString(new AuditEvent(
                "evt-1",
                "cashier-service",
                "PAYMENT_CONFIRMED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                new AuditActor("cashier-1", "Cashier", "CASHIER", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("amount", "0.01")));

        listener.onMessage(payload);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(service).record(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().actor().name()).isEqualTo("Cashier");
    }

    @Test
    void onMessageIgnoresInvalidPayload() {
        AuditEventListener listener = new AuditEventListener(objectMapper, service);

        listener.onMessage("not-json");

        verify(service, never()).record(any());
    }
}
