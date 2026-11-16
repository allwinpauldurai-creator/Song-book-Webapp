package com.example.songbook.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs (token purge, storage cleanup). Disabled with {@code songbook.jobs-enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "songbook", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
