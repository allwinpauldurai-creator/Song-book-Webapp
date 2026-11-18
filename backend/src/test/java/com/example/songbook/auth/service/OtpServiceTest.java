package com.example.songbook.auth.service;

import com.example.songbook.auth.entity.OtpCode;
import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.auth.repository.OtpCodeRepository;
import com.example.songbook.common.exception.ApiException;
import com.example.songbook.config.AppProperties;
import com.example.songbook.sms.OtpSmsService;
import com.example.songbook.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cooldown, daily limit and attempt counting (the integration tests run with the cooldown switched off). */
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final String MOBILE = "+919876543210";

    private final OtpCodeRepository codes = mock(OtpCodeRepository.class);
    private final OtpSmsService sms = mock(OtpSmsService.class);
    private final User user = new User("Test", MOBILE, null, "{noop}x");
    private OtpService service;

    @BeforeEach
    void setUp() {
        AppProperties.Sms config = new AppProperties.Sms("log", true, Duration.ofMinutes(10), 3,
                Duration.ofSeconds(60), 100, null);
        AppProperties props = new AppProperties("UTC", "http://localhost", null, null, null, null, config, null, true);
        service = new OtpService(codes, sms, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OtpCode issued(Instant createdAt, String mobile) {
        return new OtpCode(user, OtpPurpose.PASSWORD_RESET, mobile, "hash", createdAt,
                createdAt.plus(Duration.ofMinutes(10)));
    }

    @Test
    void secondCodeWithinTheCooldownIsNotSent() {
        when(codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), eq(OtpPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(issued(NOW.minusSeconds(30), MOBILE)));
        assertThat(service.issue(user, OtpPurpose.PASSWORD_RESET, MOBILE)).isFalse();
        verify(sms, never()).send(anyString(), any(), anyString());
    }

    @Test
    void codeAfterTheCooldownOrToAnotherNumberIsSent() {
        when(codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), eq(OtpPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(issued(NOW.minusSeconds(61), MOBILE)));
        assertThat(service.issue(user, OtpPurpose.PASSWORD_RESET, MOBILE)).isTrue();

        when(codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), eq(OtpPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(issued(NOW.minusSeconds(5), "+919000000000")));
        assertThat(service.issue(user, OtpPurpose.PASSWORD_RESET, MOBILE)).isTrue();

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sms, org.mockito.Mockito.times(2)).send(eq(MOBILE), eq(OtpPurpose.PASSWORD_RESET), code.capture());
        assertThat(code.getAllValues()).allMatch(c -> c.matches("\\d{6}"));
    }

    @Test
    void dailyLimitStopsSending() {
        when(codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(codes.countByCreatedAtAfter(NOW.minus(Duration.ofDays(1)))).thenReturn(100L);
        assertThatThrownBy(() -> service.issue(user, OtpPurpose.PASSWORD_RESET, MOBILE))
                .isInstanceOf(ApiException.class);
        verify(sms, never()).send(anyString(), any(), anyString());
    }

    @Test
    void wrongGuessesBurnTheCode() {
        OtpCode code = issued(NOW.minusSeconds(10), MOBILE);
        when(codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), eq(OtpPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(code));
        for (int i = 0; i < 3; i++) {
            assertThat(service.verify(user, OtpPurpose.PASSWORD_RESET, "000000")).isEmpty();
        }
        assertThat(code.getAttempts()).isEqualTo(3);
        assertThat(code.isUsable(NOW)).isFalse();
    }
}
