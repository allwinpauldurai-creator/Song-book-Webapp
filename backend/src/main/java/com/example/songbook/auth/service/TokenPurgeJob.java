package com.example.songbook.auth.service;

import com.example.songbook.auth.repository.OtpCodeRepository;
import com.example.songbook.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes SMS codes and refresh tokens that expired more than a week ago (kept briefly for reuse detection,
 * investigation and the daily SMS limit).
 */
@Component
public class TokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeJob.class);

    private final OtpCodeRepository otpCodes;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenPurgeJob(OtpCodeRepository otpCodes, RefreshTokenRepository refreshTokens, Clock clock) {
        this.otpCodes = otpCodes;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(7));
        int a = otpCodes.deleteExpiredBefore(cutoff);
        int b = refreshTokens.deleteExpiredBefore(cutoff);
        log.info("Purged {} SMS codes and {} refresh tokens", a, b);
    }
}
