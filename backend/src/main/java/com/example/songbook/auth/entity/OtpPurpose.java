package com.example.songbook.auth.entity;

/** What an SMS one-time code confirms. Registration needs no code. */
public enum OtpPurpose {
    /** Lets a user who forgot their password set a new one. */
    PASSWORD_RESET,
    /** Confirms a new mobile number before it replaces the current one. */
    MOBILE_CHANGE
}
