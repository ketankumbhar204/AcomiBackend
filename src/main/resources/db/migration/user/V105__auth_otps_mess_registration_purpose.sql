-- MESS_REGISTRATION lets an anonymous public-website visitor prove a mobile number
-- before a mess lead is stored. Separate from PROPERTY_REGISTRATION so a property
-- verification token cannot submit a mess lead, and vice versa.

ALTER TABLE auth_otps DROP CONSTRAINT IF EXISTS chk_auth_otps_purpose;

ALTER TABLE auth_otps
    ADD CONSTRAINT chk_auth_otps_purpose
        CHECK (purpose IN (
            'REGISTER',
            'LOGIN',
            'RESET_PASSWORD',
            'ACCOUNT_DELETION',
            'CHANGE_MOBILE',
            'PROPERTY_REGISTRATION',
            'MESS_REGISTRATION'
        ));
