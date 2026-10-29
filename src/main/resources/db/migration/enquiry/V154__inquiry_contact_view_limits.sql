-- Instant contact-view quotas are separate from email and in-app enquiry quotas.
-- Existing rows keep the limits the admin screens already displayed as defaults.

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS view_here_daily_limit INTEGER NOT NULL DEFAULT 5;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS web_view_here_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS android_view_here_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS android_view_here_daily_limit INTEGER NOT NULL DEFAULT 5;

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_view_here_daily_limit;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_view_here_daily_limit
        CHECK (view_here_daily_limit >= 0);

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_android_view_here_daily_limit;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_android_view_here_daily_limit
        CHECK (android_view_here_daily_limit >= 0);
