package com.example.songbook.sms;

/** The SMS provider refused or failed to send. The message is the provider's reason and never holds the code. */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message) {
        super(message);
    }
}
