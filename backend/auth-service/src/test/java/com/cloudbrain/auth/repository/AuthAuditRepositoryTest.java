package com.cloudbrain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.cloudbrain.auth.audit.AuditPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuthAuditRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    AuditPublisher auditPublisher;

    @Test
    void recordTruncatesValuesAndPublishesExtraDetails() {
        AuthAuditRepository repository = new AuthAuditRepository(jdbcTemplate, auditPublisher);
        String longValue = "x".repeat(600);

        repository.record(
                "LOGIN",
                longValue,
                longValue,
                longValue,
                longValue,
                false,
                longValue,
                longValue,
                longValue,
                Map.of("purpose", "LOGIN"));

        ArgumentCaptor<Object> updateArgs = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(any(String.class), any(), updateArgs.capture(), updateArgs.capture(), updateArgs.capture(),
                updateArgs.capture(), updateArgs.capture(), updateArgs.capture(), updateArgs.capture());
        verify(auditPublisher).publishAuthEvent(
                "LOGIN",
                longValue.substring(0, 128),
                longValue.substring(0, 128),
                longValue.substring(0, 128),
                longValue.substring(0, 32),
                false,
                longValue.substring(0, 128),
                longValue.substring(0, 64),
                longValue.substring(0, 512),
                Map.of("purpose", "LOGIN"));
    }

    @Test
    void simpleRecordUsesEmptyDetails() {
        AuthAuditRepository repository = new AuthAuditRepository(jdbcTemplate, auditPublisher);

        repository.record("LOGIN", "user-1", "Doctor", "user-1", "OUTPATIENT_DOCTOR", true, null, "127.0.0.1", "browser");

        verify(auditPublisher).publishAuthEvent(
                "LOGIN", "user-1", "Doctor", "user-1", "OUTPATIENT_DOCTOR", true, null, "127.0.0.1", "browser", Map.of());
    }
}
