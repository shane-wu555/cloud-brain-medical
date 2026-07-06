package com.cloudbrain.audit.amqp;

import com.cloudbrain.audit.service.AuditLogService;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {
    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final ObjectMapper objectMapper;
    private final AuditLogService service;

    public AuditEventListener(ObjectMapper objectMapper, AuditLogService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = "${audit.queue:audit.log.persist}")
    public void onMessage(String payload) {
        AuditEvent event = deserialize(payload);
        if (event != null) {
            service.record(event);
        }
    }

    private AuditEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AuditEvent.class);
        } catch (Exception error) {
            log.error("Failed to deserialize audit event payload: {}", payload, error);
            return null;
        }
    }
}
