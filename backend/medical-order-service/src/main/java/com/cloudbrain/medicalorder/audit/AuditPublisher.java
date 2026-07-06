package com.cloudbrain.medicalorder.audit;

import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditPublisher {
    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;
    private final String serviceName;

    public AuditPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${audit.exchange:audit.events}") String exchange,
            @Value("${audit.routing-key:audit.event}") String routingKey,
            @Value("${spring.application.name}") String serviceName) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.serviceName = serviceName;
    }

    public void publish(
            String action,
            String resourceType,
            String resourceId,
            String patientId,
            String businessId,
            String userId,
            String role,
            Map<String, Object> details) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(),
                serviceName,
                action,
                resourceType,
                resourceId,
                patientId,
                businessId,
                new AuditActor(userId, currentActorName(), role, currentRequestIp()),
                Instant.now(),
                details);
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, objectMapper.writeValueAsString(event));
        } catch (Exception error) {
            log.warn("Failed to publish audit event {} {} {}", action, resourceType, resourceId, error);
        }
    }

    private String currentActorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getClaimAsString("name");
        }
        return null;
    }

    private String currentRequestIp() {
        Object attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
