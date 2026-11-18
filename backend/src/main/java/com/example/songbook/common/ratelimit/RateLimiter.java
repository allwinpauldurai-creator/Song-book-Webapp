package com.example.songbook.common.ratelimit;

import com.example.songbook.common.exception.ApiException;
import com.example.songbook.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter for abuse-prone endpoints (login, registration, password reset).
 * <p>
 * Per-instance only: with more than one App Service instance, move to a shared store (Redis) or an
 * edge WAF rule. Documented in docs/SECURITY.md.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;

    public RateLimiter(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.enabled = properties.security().rateLimitEnabled();
    }

    /** Records an attempt for {@code key}; throws 429 when more than {@code limit} attempts fall within {@code window}. */
    public void check(String key, int limit, Duration window) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                throw ApiException.tooManyRequests();
            }
            deque.addLast(now);
        }
    }

    /** Drops idle keys so memory does not grow unbounded. */
    @Scheduled(fixedDelay = 600_000)
    public void evictIdle() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(1));
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
