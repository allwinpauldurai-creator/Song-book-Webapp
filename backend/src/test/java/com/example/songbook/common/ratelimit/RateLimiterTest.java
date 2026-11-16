package com.example.songbook.common.ratelimit;

import com.example.songbook.common.exception.ApiException;
import com.example.songbook.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** Mutable clock for sliding-window tests. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static AppProperties props(boolean enabled) {
        return new AppProperties("UTC", "http://localhost", new AppProperties.Cors(List.of()),
                new AppProperties.Membership(365, true, true),
                new AppProperties.Storage("local", "x", null, "c", "p", 1, 1, 100),
                new AppProperties.Security(null, null, true, "iss", "aud", Duration.ofMinutes(15),
                        Duration.ofDays(14), true, Duration.ofSeconds(60), Duration.ofHours(24), Duration.ofMinutes(30),
                        5, Duration.ofMinutes(15), enabled),
                new AppProperties.Mail("x@y"), new AppProperties.Bootstrap(null, null, null), false);
    }

    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        TestClock clock = new TestClock();
        RateLimiter limiter = new RateLimiter(clock, props(true));
        for (int i = 0; i < 3; i++) {
            limiter.check("k", 3, Duration.ofMinutes(1));
        }
        assertThatThrownBy(() -> limiter.check("k", 3, Duration.ofMinutes(1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many requests");
        assertThatCode(() -> limiter.check("other", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();

        clock.now = clock.now.plusSeconds(61);
        assertThatCode(() -> limiter.check("k", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterNeverBlocks() {
        RateLimiter limiter = new RateLimiter(new TestClock(), props(false));
        for (int i = 0; i < 100; i++) {
            limiter.check("k", 1, Duration.ofMinutes(1));
        }
    }
}
