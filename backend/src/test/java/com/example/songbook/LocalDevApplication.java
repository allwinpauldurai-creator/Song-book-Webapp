package com.example.songbook;

import com.example.songbook.email.EmailSender;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LOCAL DEVELOPMENT ONLY - runs the API without Docker or an installed PostgreSQL.
 * <p>
 * Starts an embedded PostgreSQL 16 whose data persists in {@code backend/data/postgres}, prints
 * verification/reset emails to the console instead of sending them, and creates a dev admin account.
 * <pre>
 *   mvnw.cmd spring-boot:test-run
 * </pre>
 * Lives in src/test so none of this can ever end up in the production jar.
 */
public class LocalDevApplication {

    public static void main(String[] args) {
        defaultProperty("spring.profiles.active", "local");
        defaultProperty("songbook.bootstrap.admin-email", "admin@songbook.local");
        defaultProperty("songbook.bootstrap.admin-password", "ChangeMe-Admin-123");
        SpringApplication.from(SongBookApplication::main).with(DevInfrastructure.class).run(args);
    }

    private static void defaultProperty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DevInfrastructure {

        private static final Logger log = LoggerFactory.getLogger("DEV-EMAIL");

        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws IOException {
            Path dataDir = Path.of("data", "postgres").toAbsolutePath();
            Files.createDirectories(dataDir);
            return EmbeddedPostgres.builder()
                    .setDataDirectory(dataDir)
                    .setCleanDataDirectory(false) // keep data between runs
                    .setPort(54329)
                    .start();
        }

        @Bean
        @Primary
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }

        /** Prints emails (including their one-time links) to the console. Never use outside local dev. */
        @Bean
        @Primary
        EmailSender consoleEmailSender() {
            return (to, subject, body) -> log.info("""

                    ===================== DEV EMAIL (not sent) =====================
                    To: {}
                    Subject: {}

                    {}
                    ================================================================""", to, subject, body);
        }
    }
}
