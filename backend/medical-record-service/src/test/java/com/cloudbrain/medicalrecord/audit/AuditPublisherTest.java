package com.cloudbrain.medicalrecord.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditPublisherTest {
    private final RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishSerializesAuditEventWithActorAndIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("name", "Alice")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        AuditPublisher publisher =
                new AuditPublisher(rabbitTemplate, objectMapper, "audit.exchange", "audit.event", "medical-record-service");

        publisher.publish("CREATE", "MEDICAL_RECORD", "mr-1", "patient-1", "biz-1", "user-1", "DOCTOR", Map.of("k", "v"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("audit.exchange"), eq("audit.event"), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.path("service").asText()).isEqualTo("medical-record-service");
        assertThat(payload.path("action").asText()).isEqualTo("CREATE");
        assertThat(payload.path("resourceId").asText()).isEqualTo("mr-1");
        assertThat(payload.path("actor").path("name").asText()).isEqualTo("Alice");
        assertThat(payload.path("actor").path("requestIp").asText()).isEqualTo("10.0.0.8");
        assertThat(payload.path("details").path("k").asText()).isEqualTo("v");
    }

    @Test
    void publishSwallowsBrokerFailure() {
        AuditPublisher publisher =
                new AuditPublisher(rabbitTemplate, objectMapper, "audit.exchange", "audit.event", "medical-record-service");
        doThrow(new IllegalStateException("down"))
                .when(rabbitTemplate)
                .convertAndSend(eq("audit.exchange"), eq("audit.event"), org.mockito.ArgumentMatchers.anyString());

        publisher.publish("CREATE", "MEDICAL_RECORD", "mr-1", "patient-1", "biz-1", "user-1", "DOCTOR", Map.of());
    }
}
