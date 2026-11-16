package com.example.songbook.audit;

/** Audit action names (stable strings, used for filtering). */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_TEMPORARILY_LOCKED = "ACCOUNT_TEMPORARILY_LOCKED";
    public static final String REFRESH_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";

    public static final String ADMIN_BOOTSTRAPPED = "ADMIN_BOOTSTRAPPED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String MEMBERSHIP_CREATED = "MEMBERSHIP_CREATED";
    public static final String MEMBERSHIP_UPDATED = "MEMBERSHIP_UPDATED";

    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String BOOK_CREATED = "BOOK_CREATED";
    public static final String BOOK_UPDATED = "BOOK_UPDATED";
    public static final String BOOK_COVER_REPLACED = "BOOK_COVER_REPLACED";
    public static final String BOOK_VERSION_UPLOADED = "BOOK_VERSION_UPLOADED";
    public static final String BOOK_ARCHIVED = "BOOK_ARCHIVED";

    public static final String PDF_READ_ACCESS = "PDF_READ_ACCESS";
    public static final String PDF_DOWNLOAD_ACCESS = "PDF_DOWNLOAD_ACCESS";

    public static final String MODIFICATION_REQUESTED = "MODIFICATION_REQUESTED";
    public static final String MODIFICATION_REVIEWED = "MODIFICATION_REVIEWED";
}
