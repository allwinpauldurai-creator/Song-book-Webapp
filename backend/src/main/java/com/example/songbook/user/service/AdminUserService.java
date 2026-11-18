package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.service.PasswordPolicy;
import com.example.songbook.auth.service.RefreshTokenService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
import com.example.songbook.common.util.PhoneNumbers;
import com.example.songbook.membership.service.MembershipService;
import com.example.songbook.role.Role;
import com.example.songbook.role.RoleName;
import com.example.songbook.role.RoleRepository;
import com.example.songbook.user.dto.UserDtos.AdminCreateUserRequest;
import com.example.songbook.user.dto.UserDtos.AdminUpdateUserRequest;
import com.example.songbook.user.dto.UserDtos.AdminUserDetail;
import com.example.songbook.user.dto.UserDtos.AdminUserSummary;
import com.example.songbook.user.entity.AccountStatus;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import com.example.songbook.user.repository.UserSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {

    private static final Map<String, String> SORTS = Map.of(
            "fullName", "fullName", "mobile", "mobile", "email", "email", "createdAt", "createdAt", "accountStatus", "accountStatus");

    private final UserRepository users;
    private final RoleRepository roles;
    private final MembershipService memberships;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(UserRepository users, RoleRepository roles, MembershipService memberships,
                            RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder, AuditService audit,
                            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummary> search(String q, AccountStatus status, RoleName role, Pageable pageable) {
        Pageable safe = PageableUtils.sanitize(pageable, SORTS, Sort.by("createdAt").descending());
        return PageResponse.from(users.findAll(UserSpecifications.search(q, status, role), safe),
                AdminUserSummary::from);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail get(UUID id) {
        User user = require(id);
        return AdminUserDetail.from(user, memberships.historyFor(id));
    }

    @Transactional
    public AdminUserDetail create(AdminCreateUserRequest req, UUID actorId) {
        String mobile = PhoneNumbers.normalize(req.mobile());
        String email = User.normalizeEmail(req.email());
        checkMobileFree(mobile, null);
        checkEmailFree(email, null);
        PasswordPolicy.check(req.password(), mobile);
        User user = new User(req.fullName(), mobile, email, passwordEncoder.encode(req.password()));
        user.getRoles().addAll(resolveRoles(req.roles()));
        if (req.mobileVerified()) {
            user.markMobileVerified(Instant.now(clock));
        }
        users.save(user);
        audit.record(actorId, AuditActions.USER_CREATED, "USER", user.getId(),
                Map.of("roles", sorted(req.roles()), "mobileVerified", req.mobileVerified()));
        return AdminUserDetail.from(user, memberships.historyFor(user.getId()));
    }

    /**
     * Updates name, contact details, status and roles. Refuses any change that would leave no active administrator.
     * The ADMIN role row is locked so two concurrent demotions cannot both pass the check.
     */
    @Transactional
    public AdminUserDetail update(UUID id, AdminUpdateUserRequest req, UUID actorId) {
        User user = require(id);
        ApiException.checkVersion(req.version(), user.getVersion());

        boolean wasActiveAdmin = user.hasRole(RoleName.ADMIN) && user.getAccountStatus() == AccountStatus.ACTIVE;
        boolean willBeActiveAdmin = req.roles().contains(RoleName.ADMIN) && req.accountStatus() == AccountStatus.ACTIVE;
        if (wasActiveAdmin && !willBeActiveAdmin) {
            roles.lockByName(RoleName.ADMIN);
            if (users.countActiveWithRole(RoleName.ADMIN) <= 1) {
                throw ApiException.conflict("LAST_ADMIN", "The last active administrator cannot be demoted or disabled");
            }
        }

        Set<RoleName> before = user.roleNames();
        AccountStatus statusBefore = user.getAccountStatus();
        String mobile = PhoneNumbers.normalize(req.mobile());
        String email = User.normalizeEmail(req.email());
        checkMobileFree(mobile, user.getId());
        checkEmailFree(email, user.getId());
        boolean mobileChanged = !mobile.equals(user.getMobile());
        user.setFullName(req.fullName());
        user.setMobile(mobile, req.mobileVerified(), Instant.now(clock));
        user.setEmail(email);
        user.setAccountStatus(req.accountStatus());
        user.getRoles().clear();
        user.getRoles().addAll(resolveRoles(req.roles()));
        if (req.accountStatus() == AccountStatus.ACTIVE && statusBefore != AccountStatus.ACTIVE) {
            user.resetFailedLogins();
        }
        if (req.accountStatus() != AccountStatus.ACTIVE) {
            refreshTokens.revokeAll(user.getId());
        }
        users.flush();
        audit.record(actorId, AuditActions.USER_UPDATED, "USER", user.getId(), Map.of(
                "rolesBefore", sorted(before), "rolesAfter", sorted(req.roles()),
                "statusBefore", statusBefore, "statusAfter", req.accountStatus(),
                "mobileChanged", mobileChanged, "mobileVerified", req.mobileVerified()));
        return AdminUserDetail.from(user, memberships.historyFor(user.getId()));
    }

    private void checkMobileFree(String mobile, UUID self) {
        users.findByMobile(mobile).filter(u -> !u.getId().equals(self)).ifPresent(u -> {
            throw ApiException.conflict("MOBILE_IN_USE", "Another account already uses this mobile number");
        });
    }

    private void checkEmailFree(String email, UUID self) {
        if (email == null) {
            return;
        }
        users.findByEmail(email).filter(u -> !u.getId().equals(self)).ifPresent(u -> {
            throw ApiException.conflict("EMAIL_IN_USE", "Another account already uses this email address");
        });
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        return names.stream().map(roles::require).collect(Collectors.toSet());
    }

    private static String sorted(Set<RoleName> names) {
        return names.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private User require(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
