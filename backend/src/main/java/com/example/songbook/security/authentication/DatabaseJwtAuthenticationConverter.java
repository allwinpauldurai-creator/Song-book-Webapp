package com.example.songbook.security.authentication;

import com.example.songbook.role.RoleName;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Converts a validated JWT into an authentication using the user's <em>current</em> database state,
 * so disabling a user or revoking the ADMIN role takes effect immediately, not when the token expires.
 */
@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final Clock clock;

    public DatabaseJwtAuthenticationConverter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        User user = users.findById(userId).orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        if (!user.canAuthenticate(Instant.now(clock))) {
            throw new DisabledException("Account is not active");
        }
        List<GrantedAuthority> authorities = user.roleNames().stream()
                .map(RoleName::authority)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, userId.toString());
    }
}
