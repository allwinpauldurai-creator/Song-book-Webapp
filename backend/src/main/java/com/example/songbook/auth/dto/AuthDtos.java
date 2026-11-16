package com.example.songbook.auth.dto;

import com.example.songbook.user.dto.UserDtos.MeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response bodies for /api/v1/auth. Registration has no role or status fields by design. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {
        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + "]";
        }
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {
        @Override
        public String toString() {
            return "TokenRequest[***]";
        }
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
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
