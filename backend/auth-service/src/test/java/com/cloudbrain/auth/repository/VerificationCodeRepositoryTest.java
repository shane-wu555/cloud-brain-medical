package com.cloudbrain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class VerificationCodeRepositoryTest {
    @Mock
    JdbcTemplate jdbc;

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Test
    void createStoresVerificationCodeInRedisWhenAvailable() {
        VerificationCodeRepository repository = new VerificationCodeRepository(jdbc, redis);
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(redis.opsForValue()).thenReturn(valueOperations);

        UUID id = repository.create("13800000000", "LOGIN", "hash", expiresAt);

        assertThat(id).isNotNull();
        verify(valueOperations).set(org.mockito.ArgumentMatchers.contains("cloudbrain:auth:verification:code:LOGIN:13800000000"), any(), any());
    }

    @Test
    void createFallsBackToDatabaseWhenRedisWriteFails() {
        VerificationCodeRepository repository = new VerificationCodeRepository(jdbc, redis);
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(redis.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(any(), any(), any());

        repository.create("13800000000", "REGISTER", "hash", expiresAt);

        verify(jdbc).update(eq("insert into verification_code (id, phone, purpose, code_hash, expires_at) values (?, ?, ?, ?, ?)"),
                any(), eq("13800000000"), eq("REGISTER"), eq("hash"), any());
    }

    @Test
    void latestActiveReadsRedisAndFallsBackToDatabaseWhenNeeded() {
        VerificationCodeRepository repository = new VerificationCodeRepository(jdbc, redis);
        UUID id = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.contains("cloudbrain:auth:verification:code:LOGIN:13800000000")))
                .thenReturn(id + "|" + Instant.now().plusSeconds(120).toEpochMilli() + "|hash");

        Optional<VerificationCodeRepository.VerificationCode> cached = repository.latestActive("13800000000", "LOGIN");

        assertThat(cached).isPresent();

        when(valueOperations.get(org.mockito.ArgumentMatchers.contains("cloudbrain:auth:verification:code:LOGIN:13800000001")))
                .thenReturn("bad-payload");
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("13800000001"), eq("LOGIN")))
                .thenReturn(List.of(new VerificationCodeRepository.VerificationCode(id, "db-hash", Instant.now().plusSeconds(60))));

        Optional<VerificationCodeRepository.VerificationCode> loaded = repository.latestActive("13800000001", "LOGIN");

        assertThat(loaded).contains(new VerificationCodeRepository.VerificationCode(id, "db-hash", loaded.orElseThrow().expiresAt()));
    }

    @Test
    void consumeDeletesRedisKeysOrFallsBackToDatabase() {
        VerificationCodeRepository repository = new VerificationCodeRepository(jdbc, redis);
        UUID id = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("code-key");

        assertThat(repository.consume(id)).isTrue();
        verify(redis).delete("code-key");

        when(valueOperations.get(any())).thenThrow(new RuntimeException("redis down"));
        when(jdbc.update("update verification_code set consumed_at = now() where id = ? and consumed_at is null", id)).thenReturn(1);

        assertThat(repository.consume(id)).isTrue();
    }
}
