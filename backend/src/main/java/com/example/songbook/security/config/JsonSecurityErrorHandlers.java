package com.example.songbook.security.config;

import com.example.songbook.common.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes 401/403 responses using the standard {@link ApiError} body.
 */
@Component
public class JsonSecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                write(request, response, 401, "UNAUTHORIZED", "Authentication is required");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                write(request, response, 403, "FORBIDDEN", "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status, code, message, request.getRequestURI()));
    }
}
