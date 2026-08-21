-- Password authentication for user logins.
-- Nullable so existing OTP-created rows remain valid until those users register a password.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
