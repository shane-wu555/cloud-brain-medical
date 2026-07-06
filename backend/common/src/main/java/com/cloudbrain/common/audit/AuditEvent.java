package com.cloudbrain.common.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditEvent(
        String eventId,
        String service,
        String action,
        String resourceType,
        String resourceId,
        String patientId,
        String businessId,
        AuditActor actor,
        Instant occurredAt,
        Map<String, Object> details) {

    public AuditEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
