package com.cloudbrain.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@SpringBootTest(
        classes = SecurityConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "security.jwt.secret=12345678901234567890123456789012",
                "security.jwt.issuer=test-issuer"
        })
class GatewaySecurityContextTest {
    @Autowired
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    SecurityWebFilterChain securityWebFilterChain;

    @Test
    void contextLoadsSecurityBeans() {
        assertThat(jwtDecoder).isNotNull();
        assertThat(securityWebFilterChain).isNotNull();
    }
}
