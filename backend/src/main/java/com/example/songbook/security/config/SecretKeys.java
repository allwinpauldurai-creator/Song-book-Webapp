package com.example.songbook.security.config;

import com.example.songbook.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Resolves HMAC keys for JWTs and PDF access tickets. In production both must be supplied
 * (Key Vault); random keys are generated only when {@code allow-generated-secrets} is true.
 */
@Component
public class SecretKeys {

    private static final Logger log = LoggerFactory.getLogger(SecretKeys.class);

    private final SecretKey jwtKey;
    private final SecretKey ticketKey;

    public SecretKeys(AppProperties properties) {
        AppProperties.Security security = properties.security();
        this.jwtKey = resolve("songbook.security.jwt-secret", security.jwtSecret(), security.allowGeneratedSecrets());
        this.ticketKey = resolve("songbook.security.ticket-secret", security.ticketSecret(), security.allowGeneratedSecrets());
    }

    public SecretKey jwtKey() {
        return jwtKey;
    }

    public SecretKey ticketKey() {
        return ticketKey;
    }

    private static SecretKey resolve(String name, String base64, boolean allowGenerated) {
        byte[] bytes;
        if (base64 == null || base64.isBlank()) {
            if (!allowGenerated) {
                throw new IllegalStateException(name + " must be set (base64, at least 32 bytes)");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("{} not set - using a random key; tokens will be invalid after restart", name);
        } else {
            try {
                bytes = Base64.getDecoder().decode(base64.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(name + " must be valid base64");
            }
            if (bytes.length < 32) {
                throw new IllegalStateException(name + " must decode to at least 32 bytes");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
