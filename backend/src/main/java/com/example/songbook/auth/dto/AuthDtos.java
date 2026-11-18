package com.example.songbook.auth.dto;

import com.example.songbook.user.dto.UserDtos.MeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for /api/v1/auth. Registration has no role or status fields by design.
 * Mobile numbers may be typed loosely ("98765 43210", "+91 98765-43210"); the service normalises them.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Six digits; surrounding spaces are tolerated. */
    public static final String CODE_PATTERN = "^\\s*\\d{6}\\s*$";

    /** @param email optional contact address, never used to sign in */
    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(max = 25) String mobile,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {
        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    public record MobileRequest(@NotBlank @Size(max = 25) String mobile) {
        @Override
        public String toString() {
            return "MobileRequest[***]";
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 25) String mobile,
            @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[***]";
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 25) String mobile,
            @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {
    }

    public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record MessageResponse(String message) {
    }
}
