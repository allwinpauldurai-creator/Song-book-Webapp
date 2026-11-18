package com.example.songbook.sms;

import com.example.songbook.auth.entity.OtpPurpose;
import com.example.songbook.common.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends codes only after the surrounding transaction commits (a code is never sent for a row that was rolled
 * back). Send failures are logged without the code and do not fail the request; the user can ask for a new code.
 */
@Service
public class OtpSmsService {

    private static final Logger log = LoggerFactory.getLogger(OtpSmsService.class);

    private final ApplicationEventPublisher events;
    private final SmsSender sender;

    public OtpSmsService(ApplicationEventPublisher events, SmsSender sender) {
        this.events = events;
        this.sender = sender;
    }

    public void send(String mobile, OtpPurpose purpose, String code) {
        events.publishEvent(new OutgoingOtp(mobile, purpose, code));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(OutgoingOtp otp) {
        try {
            sender.sendOtp(otp.mobile(), otp.purpose(), otp.code());
        } catch (SmsSendException e) {
            log.warn("Failed to send {} SMS to {}: {}", otp.purpose(), PhoneNumbers.mask(otp.mobile()), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to send {} SMS to {}: {}", otp.purpose(), PhoneNumbers.mask(otp.mobile()),
                    e.getClass().getSimpleName());
        }
    }

    public record OutgoingOtp(String mobile, OtpPurpose purpose, String code) {
        @Override
        public String toString() {
            return "OutgoingOtp[" + purpose + " to " + PhoneNumbers.mask(mobile) + "]"; // never print the code
        }
    }
}
