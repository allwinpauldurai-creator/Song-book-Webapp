package com.example.songbook;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LOCAL DEVELOPMENT ONLY - runs the API without Docker or an installed PostgreSQL.
 * <p>
 * Starts an embedded PostgreSQL 16 whose data persists in {@code backend/data/postgres}, prints
 * SMS one-time codes to the console (logger DEV-SMS, from the {@code local} profile) instead of sending them,
 * and creates a dev admin account that signs in with mobile 9999999999 (an existing dev admin without a mobile
 * gets it attached on startup).
 * <pre>
 *   mvnw.cmd spring-boot:test-run
 * </pre>
 * Lives in src/test so none of this can ever end up in the production jar.
 */
public class LocalDevApplication {

    public static void main(String[] args) {
        defaultProperty("spring.profiles.active", "local");
        defaultProperty("songbook.bootstrap.admin-email", "admin@songbook.local");
        defaultProperty("songbook.bootstrap.admin-mobile", "+919999999999");
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

        /**
         * Pooled like production (Hikari, 10 connections). {@code getPostgresDatabase()} alone is an
         * unpooled PGSimpleDataSource: every query opened a new connection (a new postgres process on
         * Windows), capping the local API at ~30 requests/s regardless of endpoint.
         */
        @Bean(destroyMethod = "close")
        @Primary
        HikariDataSource dataSource(EmbeddedPostgres postgres) {
            HikariDataSource pool = new HikariDataSource();
            pool.setDataSource(postgres.getPostgresDatabase());
            pool.setPoolName("dev-embedded-postgres");
            pool.setMaximumPoolSize(10);
            return pool;
        }
    }
}
