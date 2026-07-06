package com.cloudbrain.common.audit;

public record AuditActor(
        String userId,
        String name,
        String role,
        String requestIp) {
}
