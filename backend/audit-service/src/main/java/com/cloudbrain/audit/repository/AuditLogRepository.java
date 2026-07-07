package com.cloudbrain.audit.repository;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.service.AuditLogSearchCriteria;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(AuditEvent event) {
        AuditActor actor = event.actor();
        jdbcTemplate.update("""
                insert into audit_log (
                    event_id, user_id, actor_name, role, service,
                    resource_type, resource_id, patient_id, business_id,
                    action, request_ip, occurred_at, details
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                on conflict (event_id) do nothing
                """,
                event.eventId(),
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.name(),
                actor == null ? null : actor.role(),
                event.service(),
                event.resourceType(),
                event.resourceId(),
                event.patientId(),
                event.businessId(),
                event.action(),
                actor == null ? null : actor.requestIp(),
                Timestamp.from(event.occurredAt()),
                writeJson(event.details()));
    }

    public List<AuditLogEntry> search(AuditLogSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                select id, event_id, user_id, actor_name, role, service,
                       resource_type, resource_id, patient_id, business_id,
                       action, request_ip, occurred_at, details
                from audit_log
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendEquals(sql, args, "service", criteria.service());
        appendEquals(sql, args, "action", criteria.action());
        appendEquals(sql, args, "resource_type", criteria.resourceType());
        appendEquals(sql, args, "resource_id", criteria.resourceId());
        appendEquals(sql, args, "user_id", criteria.userId());
        appendEquals(sql, args, "patient_id", criteria.patientId());
        appendEquals(sql, args, "business_id", criteria.businessId());
        appendKeyword(sql, args, criteria.keyword());
        if (criteria.from() != null) {
            sql.append(" and occurred_at >= ?");
            args.add(Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            sql.append(" and occurred_at <= ?");
            args.add(Timestamp.from(criteria.to()));
        }
        sql.append(" order by occurred_at desc limit ?");
        args.add(Math.max(1, Math.min(criteria.limit(), 500)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditLogEntry(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("user_id"),
                        rs.getString("actor_name"),
                        rs.getString("role"),
                        rs.getString("service"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("patient_id"),
                        rs.getString("business_id"),
                        rs.getString("action"),
                        rs.getString("request_ip"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        readJson(rs.getString("details"))),
                args.toArray());
    }

    public List<AuditLogEntry> findByEventIds(List<String> eventIds) {
        List<String> ids = eventIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<AuditLogEntry> rows = jdbcTemplate.query("""
                select id, event_id, user_id, actor_name, role, service,
                       resource_type, resource_id, patient_id, business_id,
                       action, request_ip, occurred_at, details
                from audit_log
                where event_id in (""" + placeholders + """
                )
                """, (rs, rowNum) -> new AuditLogEntry(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("user_id"),
                        rs.getString("actor_name"),
                        rs.getString("role"),
                        rs.getString("service"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("patient_id"),
                        rs.getString("business_id"),
                        rs.getString("action"),
                        rs.getString("request_ip"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        readJson(rs.getString("details"))),
                ids.toArray());
        Map<String, AuditLogEntry> byEventId = new LinkedHashMap<>();
        rows.forEach(row -> byEventId.put(row.eventId(), row));
        return ids.stream()
                .map(byEventId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<AuditLogEntry> findRecentForIndex(int limit) {
        return jdbcTemplate.query("""
                select id, event_id, user_id, actor_name, role, service,
                       resource_type, resource_id, patient_id, business_id,
                       action, request_ip, occurred_at, details
                from audit_log
                order by occurred_at desc
                limit ?
                """, (rs, rowNum) -> new AuditLogEntry(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("user_id"),
                        rs.getString("actor_name"),
                        rs.getString("role"),
                        rs.getString("service"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("patient_id"),
                        rs.getString("business_id"),
                        rs.getString("action"),
                        rs.getString("request_ip"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        readJson(rs.getString("details"))),
                Math.max(1, Math.min(limit, 20000)));
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value);
    }

    private void appendKeyword(StringBuilder sql, List<Object> args, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String like = "%" + keyword.trim().toLowerCase() + "%";
        sql.append("""
                and (
                    lower(coalesce(event_id, '')) like ?
                    or lower(coalesce(user_id, '')) like ?
                    or lower(coalesce(actor_name, '')) like ?
                    or lower(coalesce(role, '')) like ?
                    or lower(coalesce(service, '')) like ?
                    or lower(coalesce(resource_type, '')) like ?
                    or lower(coalesce(resource_id, '')) like ?
                    or lower(coalesce(patient_id, '')) like ?
                    or lower(coalesce(business_id, '')) like ?
                    or lower(coalesce(action, '')) like ?
                    or lower(coalesce(request_ip, '')) like ?
                    or lower(details::text) like ?
                )
                """);
        for (int index = 0; index < 12; index += 1) {
            args.add(like);
        }
    }

    private String writeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize audit details", error);
        }
    }

    private Map<String, Object> readJson(String details) {
        try {
            if (details == null || details.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(details, MAP_TYPE);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to deserialize audit details", error);
        }
    }
}
