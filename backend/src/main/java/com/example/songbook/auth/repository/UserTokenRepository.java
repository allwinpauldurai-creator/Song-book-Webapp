package com.example.songbook.auth.repository;

import com.example.songbook.auth.entity.TokenPurpose;
import com.example.songbook.auth.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    @Query("select t from UserToken t join fetch t.user where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<UserToken> findByHash(String hash, TokenPurpose purpose);

    /** Invalidates outstanding tokens of a purpose so only the newest one works. */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(UUID userId, TokenPurpose purpose, Instant now);

    @Modifying
    @Query("delete from UserToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
