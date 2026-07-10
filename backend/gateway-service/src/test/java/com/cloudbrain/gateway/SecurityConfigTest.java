package com.cloudbrain.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void jwtDecoderRejectsSecretShorterThanThirtyTwoCharacters() {
        assertThatThrownBy(() -> config.jwtDecoder("short-secret", "issuer"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtDecoderBuildsWithValidSecret() {
        assertThat(config.jwtDecoder("12345678901234567890123456789012", "issuer")).isNotNull();
    }
}
