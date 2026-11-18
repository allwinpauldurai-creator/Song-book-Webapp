package com.example.songbook.user.entity;

import com.example.songbook.role.Role;
import com.example.songbook.role.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An account. The mobile number (E.164) is the sign-in identity and is verified by SMS code.
 * Email is an optional, unverified contact field.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(unique = true, length = 16)
    private String mobile;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    @Column(unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected User() {
    }

    /**
     * @param mobile already normalised (see {@code PhoneNumbers.normalize}); may be null only for legacy accounts
     * @param email  optional contact address
     */
    public User(String fullName, String mobile, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.fullName = fullName.trim();
        this.mobile = mobile;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
    }

    /** Lower-cased and trimmed; blank becomes {@code null} (email is optional). */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean hasRole(RoleName name) {
        return roles.stream().anyMatch(r -> r.getName() == name);
    }

    public Set<RoleName> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isLocked(Instant now) {
        return accountStatus == AccountStatus.LOCKED || (lockedUntil != null && lockedUntil.isAfter(now));
    }

    /** Active account, not temporarily locked. */
    public boolean canAuthenticate(Instant now) {
        return accountStatus == AccountStatus.ACTIVE && !isLocked(now);
    }

    public void markMobileVerified(Instant now) {
        this.mobileVerified = true;
        this.mobileVerifiedAt = now;
    }

    /** Replaces the mobile number with one that has just been confirmed by SMS code (or vouched for by an admin). */
    public void changeMobile(String newMobile, Instant now) {
        this.mobile = newMobile;
        markMobileVerified(now);
    }

    /** Admin edit: sets the number and whether it counts as verified. */
    public void setMobile(String mobile, boolean verified, Instant now) {
        boolean changed = this.mobile == null || !this.mobile.equals(mobile);
        this.mobile = mobile;
        if (verified && (changed || !mobileVerified)) {
            markMobileVerified(now);
        } else if (!verified) {
            this.mobileVerified = false;
            this.mobileVerifiedAt = null;
        }
    }

    public void recordFailedLogin(int maxFailures, Instant lockUntil) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = lockUntil;
            failedLoginCount = 0;
        }
    }

    public void resetFailedLogins() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName.trim();
    }

    public String getMobile() {
        return mobile;
    }

    public boolean isMobileVerified() {
        return mobileVerified;
    }

    public Instant getMobileVerifiedAt() {
        return mobileVerifiedAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
