-- CHANGE_MOBILE lets a signed-in user prove ownership of a new number before
-- users.mobile_number is updated. Without this, send-otp inserts fail
-- chk_auth_otps_purpose after the SMS provider has already sent.

ALTER TABLE auth_otps DROP CONSTRAINT IF EXISTS chk_auth_otps_purpose;

ALTER TABLE auth_otps
    ADD CONSTRAINT chk_auth_otps_purpose
        CHECK (purpose IN (
            'REGISTER',
            'LOGIN',
            'RESET_PASSWORD',
            'ACCOUNT_DELETION',
            'CHANGE_MOBILE'
        ));
