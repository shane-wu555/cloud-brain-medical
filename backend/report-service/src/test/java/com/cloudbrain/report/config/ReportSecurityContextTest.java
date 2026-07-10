package com.cloudbrain.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.DefaultSecurityFilterChain;

class ReportSecurityContextTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void securityFilterChainBuildsWithHttpSecurity() throws Exception {
        HttpSecurity http = Mockito.mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = Mockito.mock(DefaultSecurityFilterChain.class);
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        when(http.csrf(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.oauth2ResourceServer(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);

        assertThat(config.securityFilterChain(http, converter)).isSameAs(chain);
    }
}
