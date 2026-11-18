package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;

/** Sends a one-time code by SMS. Implementations must never log the code outside local development. */
public interface SmsSender {

    /**
     * @param mobile E.164 number, e.g. {@code +919876543210}
     * @param code   the one-time code (digits)
     */
    void sendOtp(String mobile, OtpPurpose purpose, String code);
}
