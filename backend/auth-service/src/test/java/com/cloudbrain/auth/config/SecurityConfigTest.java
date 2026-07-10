package com.cloudbrain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void jwtSecretKeyRejectsShortSecret() {
        assertThatThrownBy(() -> config.jwtSecretKey("short-secret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordEncoderHashesAndMatchesPassword() {
        String encoded = config.passwordEncoder().encode("secret");

        assertThat(config.passwordEncoder().matches("secret", encoded)).isTrue();
    }

    @Test
    void jwtEncoderAndDecoderBuildForValidSecret() {
        SecretKey key = config.jwtSecretKey("12345678901234567890123456789012");

        assertThat(config.jwtEncoder(key)).isNotNull();
        assertThat(config.jwtDecoder(key, "issuer")).isNotNull();
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
