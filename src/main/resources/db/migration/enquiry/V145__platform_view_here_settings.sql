-- Separate instant contact-view switches and a mobile daily allowance.
-- Website view_here_daily_limit stays the web allowance.

ALTER TABLE inquiry_payment_config
    ADD COLUMN web_view_here_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE inquiry_payment_config
    ADD COLUMN android_view_here_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE inquiry_payment_config
    ADD COLUMN android_view_here_daily_limit INTEGER NOT NULL DEFAULT 5;
