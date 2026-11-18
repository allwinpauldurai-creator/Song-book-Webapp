package com.example.songbook.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error body for every non-2xx API response.
 *
 * @param code        stable machine-readable error code (e.g. {@code VALIDATION_FAILED})
 * @param message     human-readable summary, safe to show to end users
 * @param fieldErrors per-field validation failures, omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }
}
