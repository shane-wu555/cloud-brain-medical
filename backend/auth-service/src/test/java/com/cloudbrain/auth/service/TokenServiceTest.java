package com.cloudbrain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbrain.auth.config.SecurityConfig;
import com.cloudbrain.auth.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class TokenServiceTest {
    @Test
    void issuedTokenContainsRoleAndExpiryAndCanBeVerified() {
        SecurityConfig config = new SecurityConfig();
        var key = config.jwtSecretKey("01234567890123456789012345678901");
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key, "cloud-brain-medical-test");
        TokenService service = new TokenService(encoder, "cloud-brain-medical-test", 60);

        String token = service.issue(new UserAccount("u-1", "doctor", "hash", "13900000000", "张医生",
                "OUTPATIENT_DOCTOR", List.of("medical-record:write"), true));
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("u-1");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OUTPATIENT_DOCTOR");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }
}
