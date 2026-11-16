package com.example.songbook.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class RequestUtils {

    private RequestUtils() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    /** Client IP; honours X-Forwarded-For only via server.forward-headers-strategy. */
    public static String clientIp() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse("unknown");
    }
}
