-- Automatic WhatsApp Business Platform send foundation for listing marketing.
-- Extends activity outcomes and adds audit fields. Does not enable live sending by itself.

ALTER TABLE admin_listing_marketing_activities
    DROP CONSTRAINT IF EXISTS chk_admin_listing_marketing_outcome;

ALTER TABLE admin_listing_marketing_activities
    ADD CONSTRAINT chk_admin_listing_marketing_outcome
    CHECK (outcome IN (
        'WHATSAPP_OPENED',
        'SHARED_CONFIRMED',
        'WHATSAPP_UNAVAILABLE',
        'CALL_ATTEMPTED',
        'CONTACTED_BY_PHONE',
        'SKIPPED',
        'WHATSAPP_API_SUBMITTED',
        'WHATSAPP_API_FAILED',
        'WHATSAPP_API_BLOCKED'
    ));

ALTER TABLE admin_listing_marketing_activities
    ADD COLUMN IF NOT EXISTS delivery_mode VARCHAR(20);

ALTER TABLE admin_listing_marketing_activities
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(120);

ALTER TABLE admin_listing_marketing_activities
    ADD COLUMN IF NOT EXISTS recipient_phone_masked VARCHAR(32);

ALTER TABLE admin_listing_marketing_activities
    DROP CONSTRAINT IF EXISTS chk_admin_listing_marketing_delivery_mode;

ALTER TABLE admin_listing_marketing_activities
    ADD CONSTRAINT chk_admin_listing_marketing_delivery_mode
    CHECK (
        delivery_mode IS NULL
        OR delivery_mode IN ('MANUAL', 'AUTOMATIC')
    );
