package com.example.songbook.auth.service;

import com.example.songbook.common.exception.ApiException;

/** Server-side password rules (length is also enforced by Bean Validation on the DTOs). */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void check(String password, String email) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be 10 to 128 characters long");
        }
        if (password.isBlank()) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must not be blank");
        }
        if (email != null && password.trim().equalsIgnoreCase(email.trim())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must not be the same as the email address");
        }
    }
}
