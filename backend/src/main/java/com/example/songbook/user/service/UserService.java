package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.service.PasswordPolicy;
import com.example.songbook.auth.service.RefreshTokenService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.user.dto.UserDtos.ChangePasswordRequest;
import com.example.songbook.user.dto.UserDtos.MeResponse;
import com.example.songbook.user.dto.UserDtos.UpdateProfileRequest;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Self-service profile operations for the authenticated user. */
@Service
public class UserService {

    private final UserRepository users;
    private final MembershipService memberships;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserService(UserRepository users, MembershipService memberships, RefreshTokenService refreshTokens,
                       PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = require(userId);
        return MeResponse.from(user, memberships.summaryFor(userId));
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = require(userId);
        user.setFullName(req.fullName());
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
        PasswordPolicy.check(req.newPassword(), user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAll(userId);
        audit.record(userId, AuditActions.PASSWORD_CHANGED, "USER", userId, null);
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
