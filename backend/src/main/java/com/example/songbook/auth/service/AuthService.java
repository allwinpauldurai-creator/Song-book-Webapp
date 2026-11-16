package com.example.songbook.auth.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.dto.AuthDtos.LoginRequest;
import com.example.songbook.auth.dto.AuthDtos.RegisterRequest;
import com.example.songbook.auth.dto.AuthDtos.ResetPasswordRequest;
import com.example.songbook.auth.entity.TokenPurpose;
import com.example.songbook.auth.entity.UserToken;
import com.example.songbook.auth.repository.UserTokenRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.ratelimit.RateLimiter;
import com.example.songbook.common.util.RequestUtils;
import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.email.AccountEmailService;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.role.RoleName;
import com.example.songbook.role.RoleRepository;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Registration, verification, login and password reset.
 * Responses for register / resend / forgot are identical whether or not the email exists (no enumeration).
 */
@Service
public class AuthService {

    /** Real hash of a random value, used to equalize timing when the email is unknown. */
    private final String dummyHash;

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final MembershipService memberships;
    private final AccountEmailService emails;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties.Security security;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AuthService(UserRepository users, RoleRepository roles, UserTokenRepository tokens,
                       RefreshTokenService refreshTokens, MembershipService memberships, AccountEmailService emails,
                       PasswordEncoder passwordEncoder, RateLimiter rateLimiter, AuditService audit,
                       AppProperties properties, PlatformTransactionManager txManager, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.memberships = memberships;
        this.emails = emails;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.security = properties.security();
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(TokenUtils.newToken());
    }

    // ------------------------------------------------------------------ registration

    public void register(RegisterRequest req) {
        String email = User.normalizeEmail(req.email());
        rateLimiter.check("register:ip:" + RequestUtils.clientIp(), 5, Duration.ofMinutes(10));
        rateLimiter.check("register:email:" + email, 3, Duration.ofMinutes(15));
        PasswordPolicy.check(req.password(), email);
        try {
            tx.executeWithoutResult(s -> {
                Optional<User> existing = users.findByEmail(email);
                if (existing.isPresent()) {
                    // Same response as a new registration. Re-send verification if still unverified.
                    User user = existing.get();
                    if (!user.isEmailVerified() && user.getAccountStatus() == AccountStatus.ACTIVE) {
                        issueVerification(user);
                    }
                    return;
                }
                User user = new User(req.fullName(), email, passwordEncoder.encode(req.password()));
                user.getRoles().add(roles.require(RoleName.MEMBER)); // never ADMIN via public registration
                users.save(user);
                audit.record(user.getId(), AuditActions.USER_REGISTERED, "USER", user.getId(), null);
                issueVerification(user);
            });
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same email: behave like the duplicate case.
        }
    }

    public void resendVerification(String rawEmail) {
        String email = User.normalizeEmail(rawEmail);
        rateLimiter.check("resend:ip:" + RequestUtils.clientIp(), 5, Duration.ofMinutes(15));
        rateLimiter.check("resend:email:" + email, 3, Duration.ofMinutes(15));
        tx.executeWithoutResult(s -> users.findByEmail(email)
                .filter(u -> !u.isEmailVerified() && u.getAccountStatus() == AccountStatus.ACTIVE)
                .ifPresent(this::issueVerification));
    }

    public void verifyEmail(String rawToken) {
        rateLimiter.check("verify:ip:" + RequestUtils.clientIp(), 20, Duration.ofMinutes(15));
        tx.executeWithoutResult(s -> {
            Instant now = Instant.now(clock);
            UserToken token = tokens.findByHash(TokenUtils.sha256Hex(rawToken), TokenPurpose.EMAIL_VERIFICATION)
                    .filter(t -> t.isUsable(now))
                    .orElseThrow(AuthService::invalidToken);
            token.markUsed(now);
            User user = token.getUser();
            if (!user.isEmailVerified()) {
                user.markEmailVerified(now);
                audit.record(user.getId(), AuditActions.EMAIL_VERIFIED, "USER", user.getId(), null);
            }
            memberships.createDefaultIfAbsent(user);
        });
    }

    private void issueVerification(User user) {
        Instant now = Instant.now(clock);
        tokens.invalidateOutstanding(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now);
        String raw = TokenUtils.newToken();
        tokens.save(new UserToken(user, TokenPurpose.EMAIL_VERIFICATION, TokenUtils.sha256Hex(raw), now,
                now.plus(security.verificationTokenTtl())));
        emails.sendVerification(user.getEmail(), user.getFullName(), raw);
    }

    // ------------------------------------------------------------------ login

    public LoginResult login(LoginRequest req) {
        String email = User.normalizeEmail(req.email());
        rateLimiter.check("login:ip:" + RequestUtils.clientIp(), 20, Duration.ofMinutes(5));
        rateLimiter.check("login:email:" + email, 10, Duration.ofMinutes(5));

        // Failed attempts are committed before the error is thrown, so lockout counters persist.
        LoginResult result = tx.execute(s -> {
            Instant now = Instant.now(clock);
            Optional<User> found = users.findByEmail(email);
            if (found.isEmpty()) {
                passwordEncoder.matches(req.password(), dummyHash);
                return LoginResult.failure("INVALID_CREDENTIALS");
            }
            User user = found.get();
            if (user.isLocked(now)) {
                return LoginResult.failure("ACCOUNT_LOCKED");
            }
            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                user.recordFailedLogin(security.maxFailedLogins(), now.plus(security.lockDuration()));
                audit.record(user.getId(), AuditActions.LOGIN_FAILED, "USER", user.getId(), null);
                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                    audit.record(user.getId(), AuditActions.ACCOUNT_TEMPORARILY_LOCKED, "USER", user.getId(), null);
                }
                return LoginResult.failure("INVALID_CREDENTIALS");
            }
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                return LoginResult.failure("ACCOUNT_DISABLED");
            }
            if (!user.isEmailVerified()) {
                return LoginResult.failure("EMAIL_NOT_VERIFIED");
            }
            user.resetFailedLogins();
            audit.record(user.getId(), AuditActions.LOGIN_SUCCEEDED, "USER", user.getId(), null);
            String refresh = refreshTokens.issueNewFamily(user);
            return LoginResult.success(user, refresh);
        });

        if (result.failureCode() != null) {
            throw switch (result.failureCode()) {
                case "ACCOUNT_LOCKED" -> ApiException.unauthorized("ACCOUNT_LOCKED",
                        "Account is temporarily locked. Try again later.");
                case "ACCOUNT_DISABLED" -> ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
                case "EMAIL_NOT_VERIFIED" -> ApiException.forbidden("EMAIL_NOT_VERIFIED",
                        "Please verify your email address before logging in");
                default -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
            };
        }
        return result;
    }

    public record LoginResult(User user, String refreshToken, String failureCode) {
        static LoginResult success(User user, String refreshToken) {
            return new LoginResult(user, refreshToken, null);
        }

        static LoginResult failure(String code) {
            return new LoginResult(null, null, code);
        }
    }

    // ------------------------------------------------------------------ password reset

    public void forgotPassword(String rawEmail) {
        String email = User.normalizeEmail(rawEmail);
        rateLimiter.check("forgot:ip:" + RequestUtils.clientIp(), 5, Duration.ofMinutes(15));
        rateLimiter.check("forgot:email:" + email, 3, Duration.ofMinutes(15));
        tx.executeWithoutResult(s -> users.findByEmail(email)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .ifPresent(user -> {
                    Instant now = Instant.now(clock);
                    tokens.invalidateOutstanding(user.getId(), TokenPurpose.PASSWORD_RESET, now);
                    String raw = TokenUtils.newToken();
                    tokens.save(new UserToken(user, TokenPurpose.PASSWORD_RESET, TokenUtils.sha256Hex(raw), now,
                            now.plus(security.resetTokenTtl())));
                    audit.record(user.getId(), AuditActions.PASSWORD_RESET_REQUESTED, "USER", user.getId(), null);
                    emails.sendPasswordReset(user.getEmail(), user.getFullName(), raw);
                }));
    }

    public void resetPassword(ResetPasswordRequest req) {
        rateLimiter.check("reset:ip:" + RequestUtils.clientIp(), 10, Duration.ofMinutes(15));
        tx.executeWithoutResult(s -> {
            Instant now = Instant.now(clock);
            UserToken token = tokens.findByHash(TokenUtils.sha256Hex(req.token()), TokenPurpose.PASSWORD_RESET)
                    .filter(t -> t.isUsable(now))
                    .orElseThrow(AuthService::invalidToken);
            User user = token.getUser();
            PasswordPolicy.check(req.newPassword(), user.getEmail());
            token.markUsed(now);
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            user.resetFailedLogins();
            refreshTokens.revokeAll(user.getId());
            audit.record(user.getId(), AuditActions.PASSWORD_RESET, "USER", user.getId(), null);
        });
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("INVALID_TOKEN", "The link is invalid or has expired");
    }

}
