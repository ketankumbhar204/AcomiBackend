-- PROPERTY_REGISTRATION lets an anonymous public-website visitor prove ownership of a
-- mobile number before a property lead is stored. Without this, send-otp inserts fail
-- chk_auth_otps_purpose after the SMS provider has already sent.

ALTER TABLE auth_otps DROP CONSTRAINT IF EXISTS chk_auth_otps_purpose;

ALTER TABLE auth_otps
    ADD CONSTRAINT chk_auth_otps_purpose
        CHECK (purpose IN (
            'REGISTER',
            'LOGIN',
            'RESET_PASSWORD',
            'ACCOUNT_DELETION',
            'CHANGE_MOBILE',
            'PROPERTY_REGISTRATION'
        ));
