package com.cloudbrain.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AuditPublisherTest {
    private final RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishAuthEventBuildsExpectedPayload() throws Exception {
        AuditPublisher publisher = new AuditPublisher(rabbitTemplate, objectMapper, "audit.exchange", "audit.event", "auth-service");

        publisher.publishAuthEvent(
                "LOGIN",
                "doctor",
                "Alice",
                "u-1",
                "ADMIN",
                true,
                null,
                "10.0.0.8",
                "Mozilla",
                Map.of("channel", "sms"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("audit.exchange"), eq("audit.event"), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.path("service").asText()).isEqualTo("auth-service");
        assertThat(payload.path("action").asText()).isEqualTo("LOGIN");
        assertThat(payload.path("resourceType").asText()).isEqualTo("AUTH_ACCOUNT");
        assertThat(payload.path("resourceId").asText()).isEqualTo("u-1");
        assertThat(payload.path("actor").path("name").asText()).isEqualTo("Alice");
        assertThat(payload.path("actor").path("requestIp").asText()).isEqualTo("10.0.0.8");
        assertThat(payload.path("details").path("success").asBoolean()).isTrue();
        assertThat(payload.path("details").path("channel").asText()).isEqualTo("sms");
    }

    @Test
    void publishAuthEventSwallowsBrokerFailure() {
        AuditPublisher publisher = new AuditPublisher(rabbitTemplate, objectMapper, "audit.exchange", "audit.event", "auth-service");
        doThrow(new IllegalStateException("down"))
                .when(rabbitTemplate)
                .convertAndSend(eq("audit.exchange"), eq("audit.event"), anyString());

        publisher.publishAuthEvent(
                "SEND_SMS_CODE",
                "doctor",
                "Alice",
                null,
                "ADMIN",
                false,
                "rate limited",
                "10.0.0.8",
                "Mozilla",
                Map.of());
    }
}
