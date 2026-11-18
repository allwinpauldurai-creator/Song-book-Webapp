package com.example.songbook.auth.entity;

import com.example.songbook.common.persistence.AssignedIdEntity;
import com.example.songbook.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use SMS code. Only a SHA-256 hash bound to the user and purpose is stored.
 * {@link #mobile} is the number the code was sent to (the new number for {@link OtpPurpose#MOBILE_CHANGE}).
 */
@Entity
@Table(name = "otp_codes")
public class OtpCode extends AssignedIdEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(nullable = false, length = 16)
    private String mobile;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpCode() {
    }

    public OtpCode(User user, OtpPurpose purpose, String mobile, String codeHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.purpose = purpose;
        this.mobile = mobile;
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /** Counts a wrong guess; the code is burned once {@code maxAttempts} is reached. */
    public void recordFailedAttempt(int maxAttempts, Instant now) {
        attempts++;
        if (attempts >= maxAttempts) {
            usedAt = now;
        }
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public String getMobile() {
        return mobile;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
