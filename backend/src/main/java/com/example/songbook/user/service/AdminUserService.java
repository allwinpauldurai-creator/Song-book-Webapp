package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.service.PasswordPolicy;
import com.example.songbook.auth.service.RefreshTokenService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.PageableUtils;
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
            "fullName", "fullName", "email", "email", "createdAt", "createdAt", "accountStatus", "accountStatus");

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
        String email = User.normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "A user with this email already exists");
        }
        PasswordPolicy.check(req.password(), email);
        User user = new User(req.fullName(), email, passwordEncoder.encode(req.password()));
        user.getRoles().addAll(resolveRoles(req.roles()));
        if (req.emailVerified()) {
            user.markEmailVerified(Instant.now(clock));
        }
        users.save(user);
        audit.record(actorId, AuditActions.USER_CREATED, "USER", user.getId(),
                Map.of("roles", sorted(req.roles()), "emailVerified", req.emailVerified()));
        return AdminUserDetail.from(user, memberships.historyFor(user.getId()));
    }

    /**
     * Updates name, status and roles. Refuses any change that would leave no active administrator.
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
        user.setFullName(req.fullName());
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
                "statusBefore", statusBefore, "statusAfter", req.accountStatus()));
        return AdminUserDetail.from(user, memberships.historyFor(user.getId()));
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
