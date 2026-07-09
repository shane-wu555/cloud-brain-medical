package com.cloudbrain.cashier.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuditPublisherTest {
    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    HttpServletRequest request;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishIncludesActorNameAndForwardedIp() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("name", "Alice")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        AuditPublisher publisher = new AuditPublisher(
                rabbitTemplate,
                new ObjectMapper().findAndRegisterModules(),
                "audit.exchange",
                "audit.key",
                "cashier-service");
        publisher.publish(
                "PAYMENT_CONFIRMED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                "user-1",
                "CASHIER",
                Map.of("amount", "0.01"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("audit.exchange"), eq("audit.key"), payload.capture());
        String json = payload.getValue();
        assertThat(json).contains("\"service\":\"cashier-service\"");
        assertThat(json).contains("\"name\":\"Alice\"");
        assertThat(json).contains("\"requestIp\":\"10.0.0.1\"");
        assertThat(json).contains("\"resourceId\":\"payment-1\"");
    }
}
