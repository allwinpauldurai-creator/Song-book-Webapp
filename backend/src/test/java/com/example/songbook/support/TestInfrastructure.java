package com.example.songbook.support;

import com.example.songbook.email.EmailSender;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real PostgreSQL 16 (embedded binaries, no Docker needed) and an in-memory email outbox.
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
    public CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }

    public static class CapturingEmailSender implements EmailSender {
        private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_%-]+)");
        private final List<Message> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String subject, String textBody) {
            messages.add(new Message(to, subject, textBody));
        }

        /** Token from the most recent email to {@code to} whose subject contains {@code subjectPart}. */
        public String lastToken(String to, String subjectPart) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m.to().equals(to) && m.subject().contains(subjectPart)) {
                    Matcher matcher = TOKEN.matcher(m.body());
                    if (matcher.find()) {
                        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
            throw new AssertionError("No '" + subjectPart + "' email for " + to);
        }

        public long count(String to) {
            return messages.stream().filter(m -> m.to().equals(to)).count();
        }

        public record Message(String to, String subject, String body) {
        }
    }
}
