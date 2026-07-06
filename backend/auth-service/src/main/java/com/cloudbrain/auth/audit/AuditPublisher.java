package com.cloudbrain.auth.audit;

import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public void publishAuthEvent(
            String eventType,
            String account,
            String actorName,
            String userId,
            String role,
            boolean success,
            String failureReason,
            String clientIp,
            String userAgent,
            Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("success", success);
        if (failureReason != null && !failureReason.isBlank()) {
            details.put("failureReason", failureReason);
        }
        if (account != null && !account.isBlank()) {
            details.put("account", account);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            details.put("userAgent", userAgent);
        }
        if (extraDetails != null && !extraDetails.isEmpty()) {
            details.putAll(extraDetails);
        }

        String resourceType = "SEND_SMS_CODE".equals(eventType) ? "SMS_CODE" : "AUTH_ACCOUNT";
        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(),
                serviceName,
                eventType,
                resourceType,
                firstNonBlank(userId, account),
                null,
                null,
                new AuditActor(userId, actorName, role, clientIp),
                Instant.now(),
                details);
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, objectMapper.writeValueAsString(event));
        } catch (Exception error) {
            log.warn("Failed to publish auth audit event {} {}", eventType, firstNonBlank(userId, account), error);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
