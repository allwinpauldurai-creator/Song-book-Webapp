package com.example.songbook.auth.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.dto.AuthDtos.LoginRequest;
import com.example.songbook.auth.dto.AuthDtos.RegisterRequest;
import com.example.songbook.auth.dto.AuthDtos.ResetPasswordRequest;
import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.ratelimit.RateLimiter;
import com.example.songbook.common.util.PhoneNumbers;
import com.example.songbook.common.util.RequestUtils;
import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
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
 * Registration, login and password reset. The mobile number (E.164) is the identity; SMS codes come from
 * {@link OtpService}.
 * <p>
 * Registration needs no SMS code: the account and its default membership are created at once and the member is
 * signed in. The mobile stays "unverified" until the member proves it with a code (password reset or mobile
 * change). Registering a number that already has an account is refused with {@code MOBILE_ALREADY_REGISTERED}.
 * Forgot-password responses are identical whether or not the number exists.
 * Code checks run in a transaction that commits even when the code is wrong, so failed attempts are counted;
 * the error is thrown afterwards.
 */
@Service
public class AuthService {

    /** Real hash of a random value, used to equalize timing when the number is unknown. */
    private final String dummyHash;

    private final UserRepository users;
    private final RoleRepository roles;
    private final OtpService otp;
    private final RefreshTokenService refreshTokens;
    private final MembershipService memberships;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties.Security security;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AuthService(UserRepository users, RoleRepository roles, OtpService otp,
                       RefreshTokenService refreshTokens, MembershipService memberships,
                       PasswordEncoder passwordEncoder, RateLimiter rateLimiter, AuditService audit,
                       AppProperties properties, PlatformTransactionManager txManager, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.otp = otp;
        this.refreshTokens = refreshTokens;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.security = properties.security();
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(TokenUtils.newToken());
    }

    // ------------------------------------------------------------------ registration

    /**
     * Creates a MEMBER account with its default membership and signs it in. No SMS code is involved.
     *
     * @throws ApiException 409 {@code MOBILE_ALREADY_REGISTERED} when the number already has an account
     */
    public LoginResult register(RegisterRequest req) {
        String mobile = PhoneNumbers.normalize(req.mobile());
        rateLimiter.check("register:ip:" + RequestUtils.clientIp(), 5, Duration.ofMinutes(10));
        rateLimiter.check("register:mobile:" + mobile, 3, Duration.ofMinutes(15));
        PasswordPolicy.check(req.password(), mobile);
        LoginResult result;
        try {
            result = tx.execute(s -> {
                if (users.existsByMobile(mobile)) {
                    return null;
                }
                // Email is optional contact data. If another account already uses it, drop it silently rather
                // than revealing that the address is registered.
                String email = User.normalizeEmail(req.email());
                if (email != null && users.existsByEmail(email)) {
                    email = null;
                }
                User user = new User(req.fullName(), mobile, email, passwordEncoder.encode(req.password()));
                user.getRoles().add(roles.require(RoleName.MEMBER)); // never ADMIN via public registration
                users.save(user);
                audit.record(user.getId(), AuditActions.USER_REGISTERED, "USER", user.getId(), null);
                memberships.createDefaultIfAbsent(user);
                return LoginResult.success(user, refreshTokens.issueNewFamily(user));
            });
        } catch (DataIntegrityViolationException e) {
            result = null; // concurrent registration with the same number
        }
        if (result == null) {
            throw ApiException.conflict("MOBILE_ALREADY_REGISTERED",
                    "This mobile number is already registered. Sign in, or reset your password if you forgot it.");
        }
        return result;
    }

    // ------------------------------------------------------------------ login

    public LoginResult login(LoginRequest req) {
        rateLimiter.check("login:ip:" + RequestUtils.clientIp(), 20, Duration.ofMinutes(5));
        String mobile = PhoneNumbers.tryNormalize(req.mobile());
        if (mobile == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw invalidCredentials();
        }
        rateLimiter.check("login:mobile:" + mobile, 10, Duration.ofMinutes(5));

        // Failed attempts are committed before the error is thrown, so lockout counters persist.
        LoginResult result = tx.execute(s -> {
            Instant now = Instant.now(clock);
            Optional<User> found = users.findByMobile(mobile);
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
                default -> invalidCredentials();
            };
        }
        return result;
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid mobile number or password");
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

    public void forgotPassword(String rawMobile) {
        String mobile = PhoneNumbers.normalize(rawMobile);
        rateLimiter.check("forgot:ip:" + RequestUtils.clientIp(), 5, Duration.ofMinutes(15));
        rateLimiter.check("forgot:mobile:" + mobile, 3, Duration.ofMinutes(15));
        tx.executeWithoutResult(s -> users.findByMobile(mobile)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .ifPresent(user -> {
                    if (otp.issue(user, OtpPurpose.PASSWORD_RESET, mobile)) {
                        audit.record(user.getId(), AuditActions.PASSWORD_RESET_REQUESTED, "USER", user.getId(), null);
                    }
                }));
    }

    /** A successful reset also proves the user holds the number, so an unverified mobile becomes verified. */
    public void resetPassword(ResetPasswordRequest req) {
        String mobile = PhoneNumbers.normalize(req.mobile());
        rateLimiter.check("reset:ip:" + RequestUtils.clientIp(), 10, Duration.ofMinutes(15));
        rateLimiter.check("reset:mobile:" + mobile, 10, Duration.ofMinutes(15));
        PasswordPolicy.check(req.newPassword(), mobile);
        Boolean reset = tx.execute(s -> {
            Optional<User> found = users.findByMobile(mobile)
                    .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE);
            if (found.isEmpty()) {
                return false;
            }
            User user = found.get();
            boolean matched = otp.verify(user, OtpPurpose.PASSWORD_RESET, req.code())
                    .filter(c -> c.getMobile().equals(user.getMobile()))
                    .isPresent();
            if (!matched) {
                return false;
            }
            Instant now = Instant.now(clock);
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            user.resetFailedLogins();
            if (!user.isMobileVerified()) {
                user.markMobileVerified(now);
                audit.record(user.getId(), AuditActions.MOBILE_VERIFIED, "USER", user.getId(), null);
            }
            refreshTokens.revokeAll(user.getId());
            audit.record(user.getId(), AuditActions.PASSWORD_RESET, "USER", user.getId(), null);
            return true;
        });
        if (!Boolean.TRUE.equals(reset)) {
            throw OtpService.invalidCode();
        }
    }
}
