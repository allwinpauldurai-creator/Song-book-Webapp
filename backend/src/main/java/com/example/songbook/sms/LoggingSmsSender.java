package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes codes to the log instead of sending them. For local development and the Azure dev environment until an
 * SMS account exists. Only created when {@code songbook.sms.allow-log-provider=true} (see {@link SmsConfig}).
 */
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger("DEV-SMS");

    @Override
    public void sendOtp(String mobile, OtpPurpose purpose, String code) {
        log.warn("DEV SMS (not sent) to {} [{}]: your Song Book Library code is {}", mobile, purpose, code);
    }
}
