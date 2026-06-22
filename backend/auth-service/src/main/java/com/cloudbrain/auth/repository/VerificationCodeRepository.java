package com.cloudbrain.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationCodeRepository {
    private final JdbcTemplate jdbc;

    public VerificationCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String phone, String purpose, String codeHash, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into verification_code (id, phone, purpose, code_hash, expires_at) values (?, ?, ?, ?, ?)",
                id, phone, purpose, codeHash, Timestamp.from(expiresAt));
        return id;
    }

    public Optional<VerificationCode> latestActive(String phone, String purpose) {
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
        return jdbc.update("update verification_code set consumed_at = now() where id = ? and consumed_at is null", id) == 1;
    }

    public record VerificationCode(UUID id, String codeHash, Instant expiresAt) {}
}
