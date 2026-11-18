package com.example.songbook.auth.controller;

import com.example.songbook.auth.dto.AuthDtos.AccessTokenResponse;
import com.example.songbook.auth.dto.AuthDtos.LoginRequest;
import com.example.songbook.auth.dto.AuthDtos.LoginResponse;
import com.example.songbook.auth.dto.AuthDtos.MessageResponse;
import com.example.songbook.auth.dto.AuthDtos.MobileRequest;
import com.example.songbook.auth.dto.AuthDtos.RegisterRequest;
import com.example.songbook.auth.dto.AuthDtos.ResetPasswordRequest;
import com.example.songbook.auth.service.AuthService;
import com.example.songbook.auth.service.RefreshTokenService;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.response.ApiError;
import com.example.songbook.config.AppProperties;
import com.example.songbook.security.authentication.AccessTokenService;
import com.example.songbook.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    public static final String REFRESH_COOKIE = "sb_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final MessageResponse GENERIC_SMS_MESSAGE =
            new MessageResponse("If the number is registered, a code has been sent by SMS.");

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenService accessTokens;
    private final UserService userService;
    private final AppProperties properties;

    public AuthController(AuthService authService, RefreshTokenService refreshTokens, AccessTokenService accessTokens,
                          UserService userService, AppProperties properties) {
        this.authService = authService;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.userService = userService;
        this.properties = properties;
    }

    @PostMapping("/register")
    @Operation(summary = "Self-register a member account (no SMS code) and sign it in; "
            + "409 MOBILE_ALREADY_REGISTERED if the number has an account")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return signedIn(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; returns an access token and sets the HttpOnly refresh cookie")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return signedIn(HttpStatus.OK, authService.login(request));
    }

    private ResponseEntity<LoginResponse> signedIn(HttpStatus status, AuthService.LoginResult result) {
        AccessTokenService.IssuedToken token = accessTokens.issue(result.user());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(),
                        userService.me(result.user().getId())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and issue a new access token")
    public ResponseEntity<?> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest request) {
        checkOrigin(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please log in again.");
        }
        RefreshTokenService.Rotation rotation;
        try {
            rotation = refreshTokens.rotate(refreshToken);
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                    .body(ApiError.of(e.getStatus().value(), e.getCode(), e.getMessage(), request.getRequestURI()));
        }
        AccessTokenService.IssuedToken token = accessTokens.issue(rotation.user());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotation.rawToken()).toString())
                .body(new AccessTokenResponse(token.value(), "Bearer", token.expiresInSeconds()));
    }

    /**
     * Revokes the refresh-token family and clears the cookie. Works with only the cookie, so a user whose
     * access token already expired can still log out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                       HttpServletRequest request) {
        checkOrigin(request);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revokeFamilyOf(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody MobileRequest request) {
        authService.forgotPassword(request.mobile());
        return ResponseEntity.accepted().body(GENERIC_SMS_MESSAGE);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** CSRF defence for the cookie-authenticated endpoints, in addition to SameSite=Strict. */
    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        List<String> allowed = properties.cors().allowedOrigins();
        if (origin != null && !allowed.contains(origin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_ORIGIN", "Request origin is not allowed");
        }
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.security().refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(properties.security().refreshTokenTtl())
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(properties.security().refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
