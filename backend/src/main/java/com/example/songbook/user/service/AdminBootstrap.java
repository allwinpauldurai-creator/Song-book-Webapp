package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.service.PasswordPolicy;
import com.example.songbook.common.util.PhoneNumbers;
import com.example.songbook.config.AppProperties;
import com.example.songbook.role.RoleName;
import com.example.songbook.role.RoleRepository;
import com.example.songbook.user.entity.User;
import com.example.songbook.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the first administrator from {@code songbook.bootstrap.*} when no admin exists yet.
 * Admins can never be created through public registration. Remove the bootstrap settings after first start.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties.Bootstrap config;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public AdminBootstrap(AppProperties properties, UserRepository users, RoleRepository roles,
                          PasswordEncoder passwordEncoder, AuditService audit, Clock clock) {
        this.config = properties.bootstrap();
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = User.normalizeEmail(config.adminEmail());
        String mobile = null;
        if (!isBlank(config.adminMobile())) {
            mobile = PhoneNumbers.tryNormalize(config.adminMobile());
            if (mobile == null) {
                throw new IllegalStateException("songbook.bootstrap.admin-mobile is not a valid mobile number");
            }
        }
        if (mobile == null && email == null) {
            return;
        }
        if (users.countWithRole(RoleName.ADMIN) > 0) {
            attachMobileToExistingAdmin(email, mobile);
            return;
        }
        if (isBlank(config.adminPassword())) {
            return;
        }
        if (mobile == null) {
            log.warn("Admin bootstrap skipped: songbook.bootstrap.admin-mobile is required to create an admin");
            return;
        }
        String adminMobile = mobile;
        PasswordPolicy.check(config.adminPassword(), adminMobile);
        User admin = users.findByMobile(adminMobile)
                .or(() -> email == null ? Optional.empty() : users.findByEmail(email))
                .orElseGet(() -> users.save(new User(
                        isBlank(config.adminFullName()) ? "Administrator" : config.adminFullName(),
                        adminMobile, email, passwordEncoder.encode(config.adminPassword()))));
        admin.getRoles().add(roles.require(RoleName.ADMIN));
        admin.getRoles().add(roles.require(RoleName.MEMBER));
        if (admin.getMobile() == null) {
            admin.changeMobile(adminMobile, Instant.now(clock));
        } else if (!admin.isMobileVerified()) {
            admin.markMobileVerified(Instant.now(clock));
        }
        audit.record(null, AuditActions.ADMIN_BOOTSTRAPPED, "USER", admin.getId(), null);
        log.info("Bootstrap administrator created/promoted (user id {})", admin.getId());
    }

    /**
     * Accounts created before sign-in moved to mobile numbers have no mobile and could no longer sign in.
     * Attaches the configured (pre-verified) number to the admin with the configured email, once.
     */
    private void attachMobileToExistingAdmin(String email, String mobile) {
        if (email == null || mobile == null) {
            log.info("Admin bootstrap skipped: an administrator already exists");
            return;
        }
        users.findByEmail(email)
                .filter(u -> u.hasRole(RoleName.ADMIN) && u.getMobile() == null)
                .ifPresentOrElse(admin -> {
                    if (users.existsByMobile(mobile)) {
                        log.warn("Admin bootstrap: the configured admin mobile is already used by another account");
                        return;
                    }
                    admin.changeMobile(mobile, Instant.now(clock));
                    audit.record(null, AuditActions.MOBILE_CHANGED, "USER", admin.getId(),
                            Map.of("from", "none", "to", PhoneNumbers.mask(mobile), "bootstrap", true));
                    log.info("Bootstrap mobile attached to existing administrator (user id {})", admin.getId());
                }, () -> log.info("Admin bootstrap skipped: an administrator already exists"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
