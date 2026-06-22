package com.cloudbrain.auth.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthAuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuthAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(
            String eventType,
            String username,
            String userId,
            boolean success,
            String failureReason,
            String clientIp,
            String userAgent) {
        jdbcTemplate.update("""
                insert into auth_audit_log
                    (id, event_type, username, user_id, success, failure_reason, client_ip, user_agent)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), eventType, username, userId, success,
                truncate(failureReason, 128), truncate(clientIp, 64), truncate(userAgent, 512));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
