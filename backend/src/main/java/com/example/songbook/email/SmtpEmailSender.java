package com.example.songbook.email;

import com.example.songbook.config.AppProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP sender. Locally this targets Mailpit (docker-compose); in Azure, Azure Communication Services
 * Email via its SMTP endpoint, or replace with another {@link EmailSender} implementation (decision D4).
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(String to, String subject, String textBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(textBody);
        mailSender.send(message);
    }
}
