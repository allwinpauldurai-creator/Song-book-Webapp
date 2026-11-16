package com.example.songbook.auth.service;

import com.example.songbook.auth.repository.RefreshTokenRepository;
import com.example.songbook.auth.repository.UserTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Deletes tokens that expired more than a week ago (kept briefly for reuse detection / investigation). */
@Component
public class TokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeJob.class);

    private final UserTokenRepository userTokens;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenPurgeJob(UserTokenRepository userTokens, RefreshTokenRepository refreshTokens, Clock clock) {
        this.userTokens = userTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(7));
        int a = userTokens.deleteExpiredBefore(cutoff);
        int b = refreshTokens.deleteExpiredBefore(cutoff);
        log.info("Purged {} user tokens and {} refresh tokens", a, b);
    }
}
