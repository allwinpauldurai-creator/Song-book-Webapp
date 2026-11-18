package com.example.songbook.reader.service;

import com.example.songbook.config.AppProperties;
import com.example.songbook.security.config.SecretKeys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTicketServiceTest {

    private static AppProperties props() {
        return new AppProperties("UTC", "http://localhost", new AppProperties.Cors(List.of()),
                new AppProperties.Membership(365, true, true),
                new AppProperties.Storage("local", "x", null, "c", "p", 1, 1, 100),
                new AppProperties.Security(null, null, true, "iss", "aud", Duration.ofMinutes(15),
                        Duration.ofDays(14), true, Duration.ofSeconds(60), 5, Duration.ofMinutes(15), false),
                new AppProperties.Sms("log", true, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 500, null),
                new AppProperties.Bootstrap(null, null, null, null), false);
    }

    private final AppProperties properties = props();
    private final SecretKeys keys = new SecretKeys(properties);
    private final Instant now = Instant.parse("2026-06-15T10:00:00Z");

    private AccessTicketService at(Instant instant) {
        return new AccessTicketService(keys, properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void validTicketRoundTrips() {
        UUID user = UUID.randomUUID(), book = UUID.randomUUID(), version = UUID.randomUUID();
        String ticket = at(now).issue(user, book, version, AccessTicketService.Purpose.READ).value();
        var claims = at(now.plusSeconds(30)).verify(ticket).orElseThrow();
        assertThat(claims.userId()).isEqualTo(user);
        assertThat(claims.bookId()).isEqualTo(book);
        assertThat(claims.versionId()).isEqualTo(version);
        assertThat(claims.purpose()).isEqualTo(AccessTicketService.Purpose.READ);
    }

    @Test
    void expiredTicketIsRejected() {
        String ticket = at(now).issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AccessTicketService.Purpose.DOWNLOAD).value();
        assertThat(at(now.plusSeconds(61)).verify(ticket)).isEmpty();
    }

    @Test
    void tamperedTicketIsRejected() {
        String ticket = at(now).issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AccessTicketService.Purpose.READ).value();
        String tampered = (ticket.charAt(0) == 'A' ? 'B' : 'A') + ticket.substring(1);
        assertThat(at(now).verify(tampered)).isEmpty();
        assertThat(at(now).verify("garbage")).isEmpty();
        assertThat(at(now).verify(null)).isEmpty();
    }

    @Test
    void ticketFromAnotherKeyIsRejected() {
        AccessTicketService other = new AccessTicketService(new SecretKeys(properties), properties,
                Clock.fixed(now, ZoneOffset.UTC));
        String ticket = other.issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AccessTicketService.Purpose.READ).value();
        assertThat(at(now).verify(ticket)).isEmpty();
    }
}
