package com.example.songbook.common.util;

import com.example.songbook.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "98765 43210", "+91 98765-43210", "+919876543210", "09876543210",
            "919876543210", "0091 98765 43210", " (987) 654-3210 "})
    void indianSpellingsNormaliseToE164(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo("+919876543210");
    }

    @Test
    void otherCountriesNeedTheirCountryCode() {
        assertThat(PhoneNumbers.normalize("+44 7700 900123")).isEqualTo("+447700900123");
        assertThat(PhoneNumbers.normalize("+1 (415) 555-0100")).isEqualTo("+14155550100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "5876543210", "+91 5876543210", "+910000000000", "abcdefghij",
            "98765432101", "+0123456789", "+1234567890123456"})
    void invalidNumbersAreRejected(String raw) {
        assertThat(PhoneNumbers.tryNormalize(raw)).isNull();
        assertThatThrownBy(() -> PhoneNumbers.normalize(raw))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("valid mobile number");
    }

    @Test
    void nullIsNotANumber() {
        assertThat(PhoneNumbers.tryNormalize(null)).isNull();
    }

    @Test
    void maskKeepsCountryCodeAndLastThreeDigits() {
        assertThat(PhoneNumbers.mask("+919876543210")).isEqualTo("+91*******210");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("***");
    }
}
