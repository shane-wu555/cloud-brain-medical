package com.cloudbrain.medicalrecord.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void decoderBuildsJwtDecoderForValidSecret() {
        assertThat(config.decoder("12345678901234567890123456789012", "issuer")).isNotNull();
    }

    @Test
    void converterMapsRoleClaimToSpringAuthority() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("role", "DOCTOR")
                .build();

        var authentication = config.converter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_DOCTOR");
    }
}
