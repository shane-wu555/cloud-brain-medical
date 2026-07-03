package com.cloudbrain.auth.repository;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationCodeRepository {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeRepository.class);
    private static final String CODE_PREFIX = "cloudbrain:auth:verification:code:";
    private static final String ID_PREFIX = "cloudbrain:auth:verification:id:";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public VerificationCodeRepository(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public UUID create(String phone, String purpose, String codeHash, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        if (storeInRedis(id, phone, purpose, codeHash, expiresAt)) {
            return id;
        }
        jdbc.update("insert into verification_code (id, phone, purpose, code_hash, expires_at) values (?, ?, ?, ?, ?)",
                id, phone, purpose, codeHash, Timestamp.from(expiresAt));
        return id;
    }

    public Optional<VerificationCode> latestActive(String phone, String purpose) {
        Optional<VerificationCode> cached = latestActiveFromRedis(phone, purpose);
        if (cached.isPresent()) {
            return cached;
        }
        List<VerificationCode> rows = jdbc.query("""
                select id, code_hash, expires_at from verification_code
                where phone = ? and purpose = ? and consumed_at is null and expires_at > now()
                order by created_at desc limit 1
                """, (rs, row) -> new VerificationCode(
                rs.getObject("id", UUID.class), rs.getString("code_hash"), rs.getTimestamp("expires_at").toInstant()),
                phone, purpose);
        return rows.stream().findFirst();
    }

    public boolean consume(UUID id) {
        if (consumeFromRedis(id)) {
            return true;
        }
        return jdbc.update("update verification_code set consumed_at = now() where id = ? and consumed_at is null", id) == 1;
    }

    private boolean storeInRedis(UUID id, String phone, String purpose, String codeHash, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        String codeKey = codeKey(phone, purpose);
        String idKey = idKey(id);
        try {
            redis.opsForValue().set(codeKey, id + "|" + expiresAt.toEpochMilli() + "|" + codeHash, ttl);
            redis.opsForValue().set(idKey, codeKey, ttl);
            return true;
        } catch (RuntimeException exception) {
            log.debug("Redis verification code write failed; falling back to database: {}", exception.getMessage());
            return false;
        }
    }

    private Optional<VerificationCode> latestActiveFromRedis(String phone, String purpose) {
        try {
            String payload = redis.opsForValue().get(codeKey(phone, purpose));
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            String[] parts = payload.split("\\|", 3);
            if (parts.length != 3) {
                return Optional.empty();
            }
            UUID id = UUID.fromString(parts[0]);
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[1]));
            if (!expiresAt.isAfter(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(new VerificationCode(id, parts[2], expiresAt));
        } catch (RuntimeException exception) {
            log.debug("Redis verification code read failed; falling back to database: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private boolean consumeFromRedis(UUID id) {
        try {
            String codeKey = redis.opsForValue().get(idKey(id));
            if (codeKey == null || codeKey.isBlank()) {
                return false;
            }
            redis.delete(codeKey);
            redis.delete(idKey(id));
            return true;
        } catch (RuntimeException exception) {
            log.debug("Redis verification code consume failed; falling back to database: {}", exception.getMessage());
            return false;
        }
    }

    private String codeKey(String phone, String purpose) {
        return CODE_PREFIX + purpose + ":" + phone;
    }

    private String idKey(UUID id) {
        return ID_PREFIX + UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    public record VerificationCode(UUID id, String codeHash, Instant expiresAt) {}
}
