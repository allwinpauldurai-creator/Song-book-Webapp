package com.example.songbook.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for expected business failures. The message is returned to the client,
 * so it must never contain secrets, tokens or internal storage details.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Try again later.");
    }

    /** Optimistic-lock check for client-supplied versions. */
    public static void checkVersion(Long expected, Long actual) {
        if (expected == null || !expected.equals(actual)) {
            throw conflict("CONCURRENT_MODIFICATION", "The resource was modified by someone else. Reload and try again.");
        }
    }
}
