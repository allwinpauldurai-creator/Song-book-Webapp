package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.entity.OtpCode;
import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.auth.service.OtpService;
import com.example.songbook.auth.service.PasswordPolicy;
import com.example.songbook.auth.service.RefreshTokenService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.ratelimit.RateLimiter;
import com.example.songbook.common.util.PhoneNumbers;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.user.dto.UserDtos.ChangeMobileRequest;
import com.example.songbook.user.dto.UserDtos.ChangePasswordRequest;
import com.example.songbook.user.dto.UserDtos.MeResponse;
import com.example.songbook.user.dto.UserDtos.UpdateProfileRequest;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Self-service profile operations for the authenticated user. */
@Service
public class UserService {

    private final UserRepository users;
    private final MembershipService memberships;
    private final RefreshTokenService refreshTokens;
    private final OtpService otp;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserRepository users, MembershipService memberships, RefreshTokenService refreshTokens,
                       OtpService otp, PasswordEncoder passwordEncoder, RateLimiter rateLimiter, AuditService audit,
                       Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.otp = otp;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = require(userId);
        return MeResponse.from(user, memberships.summaryFor(userId));
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = require(userId);
        String email = User.normalizeEmail(req.email());
        if (email != null && !email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "Another account already uses this email address");
        }
        user.setFullName(req.fullName());
        user.setEmail(email);
        audit.record(userId, AuditActions.PROFILE_UPDATED, "USER", userId, null);
        return MeResponse.from(user, memberships.summaryFor(userId));
    }

    /** Changing the password signs out all other sessions (refresh tokens revoked). */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = require(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        PasswordPolicy.check(req.newPassword(), user.getMobile());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAll(userId);
        audit.record(userId, AuditActions.PASSWORD_CHANGED, "USER", userId, null);
    }

    /**
     * Step 1 of a mobile change: checks the password and sends a code to the new number. The current number keeps
     * working until the code is confirmed.
     */
    @Transactional
    public void requestMobileChange(UUID userId, ChangeMobileRequest req) {
        rateLimiter.check("mobile-change:user:" + userId, 5, Duration.ofMinutes(15));
        String newMobile = PhoneNumbers.normalize(req.newMobile());
        User user = require(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (newMobile.equals(user.getMobile())) {
            throw ApiException.badRequest("SAME_MOBILE", "This is already your mobile number");
        }
        if (users.existsByMobile(newMobile)) {
            throw ApiException.conflict("MOBILE_IN_USE", "Another account already uses this mobile number");
        }
        if (!otp.issue(user, OtpPurpose.MOBILE_CHANGE, newMobile)) {
            throw ApiException.tooManyRequests();
        }
    }

    /**
     * Step 2: switches to the number the code was sent to. Wrong codes are counted (the transaction commits on
     * {@link ApiException}), so a code burns after the configured number of attempts.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MeResponse confirmMobileChange(UUID userId, String code) {
        rateLimiter.check("mobile-confirm:user:" + userId, 10, Duration.ofMinutes(15));
        User user = require(userId);
        OtpCode matched = otp.verify(user, OtpPurpose.MOBILE_CHANGE, code).orElseThrow(OtpService::invalidCode);
        String newMobile = matched.getMobile();
        boolean takenByOther = users.findByMobile(newMobile)
                .filter(other -> !Objects.equals(other.getId(), userId))
                .isPresent();
        if (takenByOther) {
            throw ApiException.conflict("MOBILE_IN_USE", "Another account already uses this mobile number");
        }
        String oldMobile = user.getMobile();
        user.changeMobile(newMobile, Instant.now(clock));
        audit.record(userId, AuditActions.MOBILE_CHANGED, "USER", userId, Map.of(
                "from", PhoneNumbers.mask(oldMobile), "to", PhoneNumbers.mask(newMobile)));
        return MeResponse.from(user, memberships.summaryFor(userId));
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
