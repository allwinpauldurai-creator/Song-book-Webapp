package com.example.songbook.security.authentication;

import com.example.songbook.config.AppProperties;
import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived access tokens. Roles in the token are informational; authorization re-reads them from the DB. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AppProperties.Security security;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, AppProperties properties, Clock clock) {
        this.encoder = encoder;
        this.security = properties.security();
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(security.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(security.jwtIssuer())
                .audience(List.of(security.jwtAudience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", user.roleNames().stream().map(RoleName::name).sorted().toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, security.accessTokenTtl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
