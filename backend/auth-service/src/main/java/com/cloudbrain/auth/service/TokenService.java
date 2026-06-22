package com.cloudbrain.auth.service;

import com.cloudbrain.auth.entity.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;

    public TokenService(
            JwtEncoder encoder,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.ttl-seconds}") long ttlSeconds) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(UserAccount account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS))
                .subject(account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole())
                .claim("permissions", account.getPermissions())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
