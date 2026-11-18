package com.example.songbook.support;

import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.sms.SmsSender;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real PostgreSQL 16 (embedded binaries, no Docker needed) and an in-memory SMS outbox.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    private static EmbeddedPostgres postgres;

    static synchronized EmbeddedPostgres postgres() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start embedded PostgreSQL", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // shutting down
                }
            }));
        }
        return postgres;
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        return postgres().getPostgresDatabase();
    }

    @Bean
    @Primary
    public CapturingSmsSender capturingSmsSender() {
        return new CapturingSmsSender();
    }

    public static class CapturingSmsSender implements SmsSender {
        private final List<Sms> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendOtp(String mobile, OtpPurpose purpose, String code) {
            messages.add(new Sms(mobile, purpose, code));
        }

        /** Code from the most recent SMS of {@code purpose} to {@code mobile} (E.164). */
        public String lastCode(String mobile, OtpPurpose purpose) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Sms m = messages.get(i);
                if (m.mobile().equals(mobile) && m.purpose() == purpose) {
                    return m.code();
                }
            }
            throw new AssertionError("No " + purpose + " SMS for " + mobile);
        }

        public long count(String mobile) {
            return messages.stream().filter(m -> m.mobile().equals(mobile)).count();
        }

        public record Sms(String mobile, OtpPurpose purpose, String code) {
        }
    }
}
