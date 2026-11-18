package com.example.songbook.auth.repository;

import com.example.songbook.auth.entity.OtpCode;
import com.example.songbook.auth.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    /** The newest code of a purpose for a user (used or not), for the resend cooldown and verification. */
    Optional<OtpCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    /** Invalidates outstanding codes of a purpose so only the newest one works. */
    @Modifying
    @Query("update OtpCode c set c.usedAt = :now where c.user.id = :userId and c.purpose = :purpose and c.usedAt is null")
    int invalidateOutstanding(UUID userId, OtpPurpose purpose, Instant now);

    /** Codes created since {@code since}, across all users (daily SMS cost guard). */
    long countByCreatedAtAfter(Instant since);

    @Modifying
    @Query("delete from OtpCode c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
