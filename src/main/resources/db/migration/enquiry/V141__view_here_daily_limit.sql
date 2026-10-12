-- Instant on-website contact views are a delivery option inside the existing
-- web enquiry allowance. They do not add extra enquiries.

ALTER TABLE inquiry_payment_config
    ADD COLUMN view_here_daily_limit INTEGER NOT NULL DEFAULT 5;

ALTER TABLE inquiry_daily_usage
    ADD COLUMN view_here_used INTEGER NOT NULL DEFAULT 0;

ALTER TABLE space_enquiry_deliveries
    DROP CONSTRAINT chk_space_enquiry_deliveries_channel;

ALTER TABLE space_enquiry_deliveries
    ADD CONSTRAINT chk_space_enquiry_deliveries_channel CHECK (
        delivery_channel IN ('APP', 'EMAIL', 'VIEW_HERE')
    );

ALTER TABLE space_enquiry_deliveries
    DROP CONSTRAINT chk_space_enquiry_deliveries_recipient;

ALTER TABLE space_enquiry_deliveries
    ADD CONSTRAINT chk_space_enquiry_deliveries_recipient CHECK (
        (delivery_channel = 'APP' AND recipient_email IS NULL)
        OR (delivery_channel = 'EMAIL' AND recipient_email IS NOT NULL)
        OR (delivery_channel = 'VIEW_HERE' AND recipient_email IS NULL)
    );

CREATE UNIQUE INDEX uq_space_enquiry_deliveries_view_here
    ON space_enquiry_deliveries (space_id, requester_user_id)
    WHERE delivery_channel = 'VIEW_HERE';
