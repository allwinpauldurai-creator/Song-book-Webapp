-- Song Book Library - registration no longer needs an SMS code.
-- Members are active straight after sign-up; SMS codes are only used for password reset and mobile change.

DELETE FROM otp_codes WHERE purpose = 'MOBILE_VERIFICATION';

ALTER TABLE otp_codes DROP CONSTRAINT ck_otp_codes_purpose;
ALTER TABLE otp_codes
    ADD CONSTRAINT ck_otp_codes_purpose CHECK (purpose IN ('PASSWORD_RESET', 'MOBILE_CHANGE'));
