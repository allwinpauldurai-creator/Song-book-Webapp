package com.example.songbook.email;

/** Outbound email. Implementations must never log message bodies (they contain single-use tokens). */
public interface EmailSender {

    void send(String to, String subject, String textBody);
}
