package com.example.songbook.auth.service;

import com.example.songbook.auth.entity.OtpCode;
import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.auth.repository.OtpCodeRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.sms.OtpSmsService;
import com.example.songbook.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Issues and checks 6-digit SMS codes. Codes are stored as SHA-256 of {@code userId:purpose:code}, burn after
 * {@code otp-max-attempts} wrong guesses, expire after {@code otp-ttl}, and a new one is issued at most once per
 * {@code resend-cooldown}. Callers run inside their own transaction.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;

    private final OtpCodeRepository codes;
    private final OtpSmsService sms;
    private final AppProperties.Sms config;
    private final Clock clock;

    public OtpService(OtpCodeRepository codes, OtpSmsService sms, AppProperties properties, Clock clock) {
        this.codes = codes;
        this.sms = sms;
        this.config = properties.sms();
        this.clock = clock;
    }

    /**
     * Sends a new code to {@code mobile}, invalidating the previous one.
     *
     * @return false when the cooldown since the last code has not passed (nothing is sent)
     * @throws ApiException 429 when {@code songbook.sms.daily-limit} codes were already sent in the last 24 h
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean issue(User user, OtpPurpose purpose, String mobile) {
        Instant now = Instant.now(clock);
        Optional<OtpCode> last = codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose);
        if (last.isPresent() && last.get().getCreatedAt().plus(config.resendCooldown()).isAfter(now)
                && last.get().getMobile().equals(mobile)) {
            return false;
        }
        if (codes.countByCreatedAtAfter(now.minus(Duration.ofDays(1))) >= config.dailyLimit()) {
            throw ApiException.tooManyRequests();
        }
        codes.invalidateOutstanding(user.getId(), purpose, now);
        String code = newCode();
        codes.save(new OtpCode(user, purpose, mobile, hash(user, purpose, code), now, now.plus(config.otpTtl())));
        sms.send(mobile, purpose, code);
        return true;
    }

    /**
     * Checks {@code code} against the newest usable code. A wrong guess is counted (and committed by the caller's
     * transaction), so callers must not roll back on {@code INVALID_CODE}; use {@link #verifyOrNull}.
     *
     * @return the matching code row (already marked used), or empty when wrong, expired or burned
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OtpCode> verify(User user, OtpPurpose purpose, String code) {
        Instant now = Instant.now(clock);
        Optional<OtpCode> latest = codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(c -> c.isUsable(now));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        OtpCode otp = latest.get();
        String candidate = code == null ? "" : code.trim();
        boolean matches = TokenUtils.constantTimeEquals(
                otp.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(user, purpose, candidate).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            otp.recordFailedAttempt(config.otpMaxAttempts(), now);
            return Optional.empty();
        }
        otp.markUsed(now);
        return Optional.of(otp);
    }

    public static ApiException invalidCode() {
        return ApiException.badRequest("INVALID_CODE", "The code is wrong or has expired. Request a new code.");
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String hash(User user, OtpPurpose purpose, String code) {
        return TokenUtils.sha256Hex(user.getId() + ":" + purpose + ":" + code);
    }
}
