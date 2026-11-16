package com.example.songbook.user.service;

import com.example.songbook.audit.AuditActions;
import com.example.songbook.audit.AuditService;
import com.example.songbook.auth.service.PasswordPolicy;
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
        if (isBlank(config.adminEmail()) || isBlank(config.adminPassword())) {
            return;
        }
        if (users.countWithRole(RoleName.ADMIN) > 0) {
            log.info("Admin bootstrap skipped: an administrator already exists");
            return;
        }
        String email = User.normalizeEmail(config.adminEmail());
        PasswordPolicy.check(config.adminPassword(), email);
        User admin = users.findByEmail(email).orElseGet(() -> users.save(new User(
                isBlank(config.adminFullName()) ? "Administrator" : config.adminFullName(),
                email, passwordEncoder.encode(config.adminPassword()))));
        admin.getRoles().add(roles.require(RoleName.ADMIN));
        admin.getRoles().add(roles.require(RoleName.MEMBER));
        if (!admin.isEmailVerified()) {
            admin.markEmailVerified(Instant.now(clock));
        }
        audit.record(null, AuditActions.ADMIN_BOOTSTRAPPED, "USER", admin.getId(), null);
        log.info("Bootstrap administrator created/promoted (user id {})", admin.getId());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
