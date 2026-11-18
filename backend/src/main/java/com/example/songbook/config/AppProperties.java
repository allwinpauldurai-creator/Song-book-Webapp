package com.example.songbook.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Application-specific configuration, bound from the {@code songbook.*} namespace.
 * Validated at startup so misconfiguration fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "songbook")
public record AppProperties(
        @NotBlank String businessTimezone,
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Membership membership,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Security security,
        @Valid @NotNull Sms sms,
        @Valid @NotNull Bootstrap bootstrap,
        @DefaultValue("true") boolean jobsEnabled) {

    public ZoneId zoneId() {
        return ZoneId.of(businessTimezone);
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * @param defaultDurationDays          length of an automatically created membership; 0 means no end date.
     *                                     The business owner must confirm this value (decision D1).
     * @param autoCreateOnMobileVerification create an ACTIVE membership when a member verifies their mobile (decision D2)
     * @param adminsBypassMembership       admins may read PDFs without a membership (decision D3)
     */
    public record Membership(
            @Min(0) int defaultDurationDays,
            @DefaultValue("true") boolean autoCreateOnMobileVerification,
            @DefaultValue("true") boolean adminsBypassMembership) {
    }

    /**
     * @param provider        {@code local} or {@code azure}
     * @param localRootDir    root directory for the {@code local} provider
     * @param azureEndpoint   blob service endpoint, e.g. https://account.blob.core.windows.net (azure provider)
     */
    public record Storage(
            @NotBlank String provider,
            String localRootDir,
            String azureEndpoint,
            @DefaultValue("book-covers") String coversContainer,
            @DefaultValue("book-pdfs") String pdfsContainer,
            @Min(1) long maxPdfBytes,
            @Min(1) long maxImageBytes,
            @DefaultValue("6000") int maxImageDimension) {
    }

    /**
     * @param jwtSecret             base64 HMAC key (>= 32 bytes). Blank only allowed with allowGeneratedSecrets.
     * @param ticketSecret          base64 HMAC key for PDF access tickets
     * @param allowGeneratedSecrets generate random secrets at startup when blank (local/test only)
     */
    public record Security(
            String jwtSecret,
            String ticketSecret,
            @DefaultValue("false") boolean allowGeneratedSecrets,
            @DefaultValue("songbook-api") String jwtIssuer,
            @DefaultValue("songbook-ui") String jwtAudience,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("14d") Duration refreshTokenTtl,
            @DefaultValue("true") boolean refreshCookieSecure,
            @DefaultValue("60s") Duration ticketTtl,
            @DefaultValue("5") int maxFailedLogins,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("true") boolean rateLimitEnabled) {
    }

    /**
     * SMS one-time codes (sign-up verification, password reset, mobile change).
     *
     * @param provider         {@code msg91} (real SMS) or {@code log} (writes codes to the log; local/dev only)
     * @param allowLogProvider must be true for {@code log} to start, so production cannot use it by accident
     * @param otpTtl           how long a code stays valid
     * @param otpMaxAttempts   wrong guesses before a code is burned
     * @param resendCooldown   minimum time between two codes for the same user and purpose
     * @param dailyLimit       codes sent in any 24 h (all users) before further requests are refused (cost guard)
     */
    public record Sms(
            @NotBlank String provider,
            @DefaultValue("false") boolean allowLogProvider,
            @DefaultValue("10m") Duration otpTtl,
            @DefaultValue("5") @Min(1) int otpMaxAttempts,
            @DefaultValue("60s") Duration resendCooldown,
            @DefaultValue("500") @Min(1) int dailyLimit,
            @Valid @NotNull @DefaultValue Msg91 msg91) {
    }

    /**
     * MSG91 Flow API. Each purpose uses its own DLT-approved template containing the {@code ##otp##} variable.
     */
    public record Msg91(
            @DefaultValue("https://control.msg91.com") String baseUrl,
            String authKey,
            String templateVerify,
            String templateReset,
            String templateChange) {
        @Override
        public String toString() {
            return "Msg91[baseUrl=" + baseUrl + ", authKey=***]";
        }
    }

    /**
     * One-time creation of the first administrator when no admin exists (email or mobile + password).
     * {@code adminMobile} is also attached, pre-verified, to an existing admin with {@code adminEmail} that has no
     * mobile yet, so accounts created before sign-in moved to mobile numbers can still sign in.
     */
    public record Bootstrap(String adminEmail, String adminMobile, String adminPassword, String adminFullName) {
        @Override
        public String toString() {
            return "Bootstrap[adminEmail=" + adminEmail + ", adminMobile=" + adminMobile + ", adminPassword=***]";
        }
    }
}
