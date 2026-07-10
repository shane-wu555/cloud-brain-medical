package com.cloudbrain.doctor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.DefaultSecurityFilterChain;

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

    @Test
    void securityFilterChainBuildsWithHttpSecurity() throws Exception {
        HttpSecurity http = Mockito.mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = Mockito.mock(DefaultSecurityFilterChain.class);

        when(http.csrf(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.oauth2ResourceServer(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);

        assertThat(config.chain(http, config.converter())).isSameAs(chain);
    }
}
