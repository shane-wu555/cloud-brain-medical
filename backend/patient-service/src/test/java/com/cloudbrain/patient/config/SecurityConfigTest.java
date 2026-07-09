package com.cloudbrain.patient.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void decoderRejectsSecretShorterThanThirtyTwoCharacters() {
        assertThatThrownBy(() -> config.decoder("short-secret", "issuer"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decoderBuildsJwtDecoderForValidSecret() {
        assertThat(config.decoder("12345678901234567890123456789012", "issuer")).isNotNull();
    }

    @Test
    void converterMapsRoleClaimToSpringAuthority() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("role", "PATIENT")
                .build();

        var authentication = config.converter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PATIENT");
    }
}
