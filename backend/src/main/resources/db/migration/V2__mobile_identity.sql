-- Song Book Library - mobile number becomes the account identity (sign-in, verification, password reset).
-- Email stays as an optional, unverified contact field. Email verification / reset links are retired.

-- ---------------------------------------------------------------------------
-- Users: mobile number (E.164, e.g. +919876543210)
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN mobile             VARCHAR(16) NULL,
    ADD COLUMN mobile_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN mobile_verified_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT uq_users_mobile UNIQUE (mobile),
    ADD CONSTRAINT ck_users_mobile_format CHECK (mobile IS NULL OR mobile ~ '^\+[1-9][0-9]{7,14}$'),
    ADD CONSTRAINT ck_users_mobile_verified_at CHECK (mobile_verified = FALSE OR mobile_verified_at IS NOT NULL),
    ADD CONSTRAINT ck_users_mobile_verified_needs_mobile CHECK (mobile_verified = FALSE OR mobile IS NOT NULL);

-- Email is optional now (UNIQUE still applies to non-NULL values; normalisation check passes for NULL).
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- One-time SMS codes. Only a SHA-256 hash bound to user + purpose is stored, never the code.
-- Not unique by hash: 6-digit codes can repeat across users.
-- ---------------------------------------------------------------------------
CREATE TABLE otp_codes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(30) NOT NULL,
    mobile     VARCHAR(16) NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER     NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_otp_codes_purpose CHECK (purpose IN ('MOBILE_VERIFICATION', 'PASSWORD_RESET', 'MOBILE_CHANGE')),
    CONSTRAINT ck_otp_codes_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_otp_codes_user ON otp_codes (user_id, purpose, created_at);

-- Email verification / reset-link tokens are no longer used.
DROP TABLE user_tokens;
