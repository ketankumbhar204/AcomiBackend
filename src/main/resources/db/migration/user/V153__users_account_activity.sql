-- Sign-in and last-use times for admin account status.
-- Existing rows stay activity_tracked = false so a null timestamp means
-- "not recorded", not "never used". New accounts set the flag in application code.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS activity_tracked BOOLEAN NOT NULL DEFAULT FALSE;
