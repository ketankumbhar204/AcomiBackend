-- Separate credit packages and access policy for WEB (email/web) vs ANDROID (mobile app).

ALTER TABLE inquiry_credit_packages
    ADD COLUMN IF NOT EXISTS client_channel VARCHAR(20) NOT NULL DEFAULT 'WEB';

ALTER TABLE inquiry_credit_packages
    DROP CONSTRAINT IF EXISTS chk_inquiry_credit_packages_channel;

ALTER TABLE inquiry_credit_packages
    ADD CONSTRAINT chk_inquiry_credit_packages_channel
        CHECK (client_channel IN ('WEB', 'ANDROID'));

UPDATE inquiry_credit_packages
SET name = '30 Email Inquiry Credits',
    client_channel = 'WEB',
    display_order = 0
WHERE id = 'a1000000-0000-4000-8000-000000000010';

INSERT INTO inquiry_credit_packages (id, name, price_amount, currency, credits, enabled, display_order, client_channel)
VALUES (
    'a1000000-0000-4000-8000-000000000011',
    '30 Mobile Inquiry Credits',
    9.00,
    'INR',
    30,
    TRUE,
    1,
    'ANDROID'
)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS web_free_daily_limit INTEGER NOT NULL DEFAULT 5;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS android_billing_mode VARCHAR(20) NOT NULL DEFAULT 'FREE';

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS android_free_daily_limit INTEGER NOT NULL DEFAULT 5;

ALTER TABLE inquiry_payment_config
    ADD COLUMN IF NOT EXISTS android_hourly_rate_limit INTEGER NOT NULL DEFAULT 20;

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_android_billing_mode;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_android_billing_mode
        CHECK (android_billing_mode IN ('FREE', 'CREDITS'));

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_web_free_daily_limit;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_web_free_daily_limit
        CHECK (web_free_daily_limit >= 0);

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_android_free_daily_limit;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_android_free_daily_limit
        CHECK (android_free_daily_limit >= 0);

ALTER TABLE inquiry_payment_config
    DROP CONSTRAINT IF EXISTS chk_inquiry_payment_android_hourly_rate_limit;

ALTER TABLE inquiry_payment_config
    ADD CONSTRAINT chk_inquiry_payment_android_hourly_rate_limit
        CHECK (android_hourly_rate_limit >= 1);
