-- LOGIN and RESET_PASSWORD were added to OtpPurpose after auth_otps was created
-- with REGISTER and ACCOUNT_DELETION only. Without this, password-reset OTP
-- inserts fail chk_auth_otps_purpose after the SMS provider has already sent.

ALTER TABLE auth_otps DROP CONSTRAINT IF EXISTS chk_auth_otps_purpose;

ALTER TABLE auth_otps
    ADD CONSTRAINT chk_auth_otps_purpose
        CHECK (purpose IN ('REGISTER', 'LOGIN', 'RESET_PASSWORD', 'ACCOUNT_DELETION'));
