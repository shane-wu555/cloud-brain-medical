package com.cloudbrain.audit.model;

import java.time.Instant;
import java.util.Map;

public record AuditLogEntry(
        Long id,
        String eventId,
        String userId,
        String actorName,
        String role,
        String service,
        String resourceType,
        String resourceId,
        String patientId,
        String businessId,
        String action,
        String requestIp,
        Instant occurredAt,
        Map<String, Object> details) {
}
