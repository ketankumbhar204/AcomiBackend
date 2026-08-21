-- Account deletion: keep the users row for FK integrity, remove personal
-- identity from the login, and allow the same mobile number to register again.
-- This script has not been applied to production; it replaces the earlier
-- deactivation-only draft of V98.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE users
    ALTER COLUMN mobile_number TYPE VARCHAR(64);

ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_mobile_number;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_mobile_number_active
    ON users (mobile_number)
    WHERE is_active = true;
