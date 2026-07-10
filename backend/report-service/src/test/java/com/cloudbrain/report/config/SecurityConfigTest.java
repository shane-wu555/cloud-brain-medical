package com.cloudbrain.report.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void jwtDecoderBuildsWithValidSecret() {
        assertThat(config.jwtDecoder("12345678901234567890123456789012", "issuer")).isNotNull();
    }

    @Test
    void jwtAuthenticationConverterMapsRoleClaimToAuthority() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("role", "ADMIN")
                .build();

        var authentication = config.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }
}
