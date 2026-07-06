package com.cloudbrain.audit.service;

import java.time.Instant;

public record AuditLogSearchCriteria(
        String service,
        String action,
        String resourceType,
        String resourceId,
        String userId,
        String patientId,
        String businessId,
        Instant from,
        Instant to,
        int limit) {
}
