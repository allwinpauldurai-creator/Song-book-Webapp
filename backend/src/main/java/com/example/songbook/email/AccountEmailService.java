package com.example.songbook.email;

import com.example.songbook.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Account emails, sent only after the surrounding transaction commits (so a link is never sent for a
 * token that was rolled back). Send failures are logged without the token and do not fail the request;
 * the user can request a new email.
 */
@Service
public class AccountEmailService {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    private final ApplicationEventPublisher events;
    private final EmailSender sender;
    private final String frontendBaseUrl;

    public AccountEmailService(ApplicationEventPublisher events, EmailSender sender, AppProperties properties) {
        this.events = events;
        this.sender = sender;
        this.frontendBaseUrl = properties.frontendBaseUrl().replaceAll("/+$", "");
    }

    public void sendVerification(String email, String name, String rawToken) {
        String link = frontendBaseUrl + "/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        events.publishEvent(new OutgoingEmail(email, "Verify your Song Book Library email", """
                Hello %s,

                Please confirm your email address by opening this link:
                %s

                The link expires soon and can be used once. If you did not register, ignore this email.
                """.formatted(name, link)));
    }

    public void sendPasswordReset(String email, String name, String rawToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        events.publishEvent(new OutgoingEmail(email, "Reset your Song Book Library password", """
                Hello %s,

                A password reset was requested for your account. Open this link to choose a new password:
                %s

                The link expires soon and can be used once. If you did not request this, ignore this email.
                """.formatted(name, link)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(OutgoingEmail email) {
        try {
            sender.send(email.to(), email.subject(), email.body());
        } catch (RuntimeException e) {
            log.warn("Failed to send email '{}': {}", email.subject(), e.getClass().getSimpleName());
        }
    }

    public record OutgoingEmail(String to, String subject, String body) {
        @Override
        public String toString() {
            return "OutgoingEmail[subject=" + subject + "]"; // never print the body (contains a token)
        }
    }
}
