package com.example.songbook.auth.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.entity.RefreshToken;
import com.example.songbook.auth.repository.RefreshTokenRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Opaque, rotating refresh tokens with reuse detection. */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final AuditService audit;
    private final AppProperties.Security security;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AuditService audit, AppProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.security = properties.security();
        this.clock = clock;
    }

    /** Starts a new token family (login). Returns the raw token for the cookie. */
    @Transactional
    public String issueNewFamily(User user) {
        return issue(user, UUID.randomUUID()).raw();
    }

    /**
     * Rotates a refresh token. A token that was already rotated or revoked indicates theft or a replay:
     * the whole family is revoked and the request fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = repository.lockByHash(TokenUtils.sha256Hex(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        if (current.getRevokedAt() != null) {
            repository.revokeFamily(current.getFamilyId(), now);
            audit.record(current.getUser().getId(), AuditActions.REFRESH_TOKEN_REUSE, "USER",
                    current.getUser().getId(), Map.of("familyId", current.getFamilyId()));
            throw invalid();
        }
        if (!current.isActive(now)) {
            throw invalid();
        }
        User user = current.getUser();
        if (!user.canAuthenticate(now)) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        Issued next = issue(user, current.getFamilyId());
        current.revoke(now, next.id());
        return new Rotation(user, next.raw());
    }

    /** Logout: revokes the presented token's family. Unknown tokens are ignored. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        repository.lockByHash(TokenUtils.sha256Hex(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    private Issued issue(User user, UUID familyId) {
        Instant now = Instant.now(clock);
        String raw = TokenUtils.newToken();
        RefreshToken token = repository.save(new RefreshToken(user, familyId, TokenUtils.sha256Hex(raw), now,
                now.plus(security.refreshTokenTtl())));
        return new Issued(token.getId(), raw);
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please log in again.");
    }

    private record Issued(UUID id, String raw) {
    }

    public record Rotation(User user, String rawToken) {
    }
}
