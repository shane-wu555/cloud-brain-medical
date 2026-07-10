package com.cloudbrain.gateway;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer) {
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 个字符");
        }
        SecretKey key = new SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                JwtValidators.createDefaultWithIssuer(issuer)));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/login", "/api/auth/register", "/api/auth/sms-codes",
                                "/api/auth/sms-login", "/api/auth/reset-password", "/api/auth/change-password", "/actuator/health",
                                "/api/payments/qr-code", "/api/payments/scan-entry",
                                "/api/refunds/qr-code", "/api/refunds/scan-entry").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
                .build();
    }
}
