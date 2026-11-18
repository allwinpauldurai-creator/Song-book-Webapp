package com.example.songbook.reader.service;

import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
import com.example.songbook.security.config.SecretKeys;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived HMAC-signed tickets authorizing one user to fetch one book version's PDF bytes.
 * Used instead of SAS URLs so eligibility is re-checked on every request and no storage URL ever leaves
 * the server. Format: base64url(payload) "." base64url(HMAC-SHA256(payload)).
 */
@Service
public class AccessTicketService {

    public enum Purpose { READ, DOWNLOAD }

    private final SecretKey key;
    private final AppProperties.Security security;
    private final Clock clock;

    public AccessTicketService(SecretKeys keys, AppProperties properties, Clock clock) {
        this.key = keys.ticketKey();
        this.security = properties.security();
        this.clock = clock;
    }

    public Ticket issue(UUID userId, UUID bookId, UUID versionId, Purpose purpose) {
        Instant expiresAt = Instant.now(clock).plus(security.ticketTtl());
        String payload = String.join("|", "v1", userId.toString(), bookId.toString(), versionId.toString(),
                purpose.name(), Long.toString(expiresAt.getEpochSecond()));
        String encoded = b64(payload.getBytes(StandardCharsets.UTF_8));
        return new Ticket(encoded + "." + b64(sign(encoded)), expiresAt);
    }

    /** Returns the claims if the signature is valid and the ticket has not expired. */
    public Optional<Claims> verify(String ticket) {
        if (ticket == null || ticket.length() > 512) {
            return Optional.empty();
        }
        int dot = ticket.indexOf('.');
        if (dot <= 0 || dot != ticket.lastIndexOf('.')) {
            return Optional.empty();
        }
        String encoded = ticket.substring(0, dot);
        try {
            byte[] signature = Base64.getUrlDecoder().decode(ticket.substring(dot + 1));
            if (!TokenUtils.constantTimeEquals(signature, sign(encoded))) {
                return Optional.empty();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 6 || !"v1".equals(parts[0])) {
                return Optional.empty();
            }
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[5]));
            if (!expiresAt.isAfter(Instant.now(clock))) {
                return Optional.empty();
            }
            return Optional.of(new Claims(UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                    UUID.fromString(parts[3]), Purpose.valueOf(parts[4]), expiresAt));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Ticket(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "Ticket[expiresAt=" + expiresAt + "]";
        }
    }

    public record Claims(UUID userId, UUID bookId, UUID versionId, Purpose purpose, Instant expiresAt) {
    }
}
