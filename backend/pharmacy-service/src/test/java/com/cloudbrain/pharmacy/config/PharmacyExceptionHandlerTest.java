package com.cloudbrain.pharmacy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PharmacyExceptionHandlerTest {
    private final PharmacyExceptionHandler handler = new PharmacyExceptionHandler();

    @Test
    void badRequestReturnsMessageMap() {
        assertThat(handler.badRequest(new IllegalArgumentException("bad request")))
                .isEqualTo(java.util.Map.of("message", "bad request"));
    }

    @Test
    void conflictReturnsMessageMap() {
        assertThat(handler.conflict(new IllegalStateException("conflict")))
                .isEqualTo(java.util.Map.of("message", "conflict"));
    }

    @Test
    void forbiddenReturnsMessageMap() {
        assertThat(handler.forbidden(new AccessDeniedException("forbidden")))
                .isEqualTo(java.util.Map.of("message", "forbidden"));
    }
}
