package com.example.songbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single injectable clock in the business timezone. All date rules (book availability,
 * membership validity) must use {@code LocalDate.now(clock)} so they are consistent and testable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
