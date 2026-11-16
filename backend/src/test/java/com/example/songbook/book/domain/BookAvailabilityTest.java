package com.example.songbook.book.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BookAvailabilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void publishedBookWithinWindowIsAvailable() {
        assertThat(BookAvailability.isAvailable(BookStatus.PUBLISHED,
                TODAY.minusDays(10), TODAY.plusDays(10), TODAY)).isTrue();
    }

    @Test
    void effectiveAndExpiryDatesAreInclusive() {
        assertThat(BookAvailability.isAvailable(BookStatus.PUBLISHED, TODAY, TODAY, TODAY)).isTrue();
    }

    @Test
    void futureEffectiveDatePreventsAvailability() {
        assertThat(BookAvailability.isAvailable(BookStatus.PUBLISHED, TODAY.plusDays(1), null, TODAY)).isFalse();
    }

    @Test
    void expiredBookIsUnavailable() {
        assertThat(BookAvailability.isAvailable(BookStatus.PUBLISHED,
                TODAY.minusDays(30), TODAY.minusDays(1), TODAY)).isFalse();
    }

    @Test
    void nullExpiryMeansNoAutomaticExpiry() {
        assertThat(BookAvailability.isAvailable(BookStatus.PUBLISHED,
                TODAY.minusYears(20), null, TODAY)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = BookStatus.class, names = {"DRAFT", "INACTIVE", "ARCHIVED"})
    void nonPublishedBooksAreUnavailable(BookStatus status) {
        assertThat(BookAvailability.isAvailable(status, TODAY.minusDays(1), null, TODAY)).isFalse();
    }
}
