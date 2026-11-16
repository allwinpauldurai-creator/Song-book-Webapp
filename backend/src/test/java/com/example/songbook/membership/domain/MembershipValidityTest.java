package com.example.songbook.membership.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipValidityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void activeMembershipWithinDatesIsValid() {
        assertThat(MembershipValidity.isValid(MembershipStatus.ACTIVE,
                TODAY.minusDays(1), TODAY.plusDays(1), TODAY)).isTrue();
    }

    @Test
    void startAndEndDatesAreInclusive() {
        assertThat(MembershipValidity.isValid(MembershipStatus.ACTIVE, TODAY, TODAY, TODAY)).isTrue();
    }

    @Test
    void futureStartDateDeniesAccess() {
        assertThat(MembershipValidity.isValid(MembershipStatus.ACTIVE, TODAY.plusDays(1), null, TODAY)).isFalse();
    }

    @Test
    void pastEndDateDeniesAccess() {
        assertThat(MembershipValidity.isValid(MembershipStatus.ACTIVE,
                TODAY.minusYears(1), TODAY.minusDays(1), TODAY)).isFalse();
    }

    @Test
    void nullEndDateMeansNoAutomaticExpiry() {
        assertThat(MembershipValidity.isValid(MembershipStatus.ACTIVE, TODAY.minusYears(5), null, TODAY)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipStatus.class, names = {"SUSPENDED", "EXPIRED", "CANCELLED"})
    void nonActiveStatusDeniesAccess(MembershipStatus status) {
        assertThat(MembershipValidity.isValid(status, TODAY.minusDays(1), null, TODAY)).isFalse();
    }
}
