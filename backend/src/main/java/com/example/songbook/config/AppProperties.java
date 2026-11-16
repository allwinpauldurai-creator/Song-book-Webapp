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
        @Valid @NotNull Mail mail,
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
     * @param autoCreateOnEmailVerification create an ACTIVE membership when a member verifies their email (decision D2)
     * @param adminsBypassMembership       admins may read PDFs without a membership (decision D3)
     */
    public record Membership(
            @Min(0) int defaultDurationDays,
            @DefaultValue("true") boolean autoCreateOnEmailVerification,
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
            @DefaultValue("24h") Duration verificationTokenTtl,
            @DefaultValue("30m") Duration resetTokenTtl,
            @DefaultValue("5") int maxFailedLogins,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("true") boolean rateLimitEnabled) {
    }

    public record Mail(@NotBlank String from) {
    }

    /**
     * One-time creation of the first administrator when no admin exists. All three values must be set.
     */
    public record Bootstrap(String adminEmail, String adminPassword, String adminFullName) {
    }
}
