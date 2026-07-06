package com.cloudbrain.auth.repository;

import com.cloudbrain.auth.audit.AuditPublisher;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthAuditRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AuditPublisher auditPublisher;

    public AuthAuditRepository(JdbcTemplate jdbcTemplate, AuditPublisher auditPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditPublisher = auditPublisher;
    }

    public void record(
            String eventType,
            String account,
            String actorName,
            String userId,
            String role,
            boolean success,
            String failureReason,
            String clientIp,
            String userAgent) {
        record(eventType, account, actorName, userId, role, success, failureReason, clientIp, userAgent, Map.of());
    }

    public void record(
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
        jdbcTemplate.update("""
                insert into auth_audit_log
                    (id, event_type, username, user_id, success, failure_reason, client_ip, user_agent)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventType,
                truncate(account, 128),
                truncate(userId, 128),
                success,
                truncate(failureReason, 128),
                truncate(clientIp, 64),
                truncate(userAgent, 512));
        auditPublisher.publishAuthEvent(
                eventType,
                truncate(account, 128),
                truncate(actorName, 128),
                truncate(userId, 128),
                truncate(role, 32),
                success,
                truncate(failureReason, 128),
                truncate(clientIp, 64),
                truncate(userAgent, 512),
                extraDetails);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
