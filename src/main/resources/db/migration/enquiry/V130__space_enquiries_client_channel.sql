-- Persist enquiry client channel so share/delivery can branch WEB (email) vs ANDROID (in-app).
-- Existing rows default to WEB (historical email delivery).

ALTER TABLE space_enquiries
    ADD COLUMN client_channel VARCHAR(20) NOT NULL DEFAULT 'WEB';

ALTER TABLE space_enquiries
    ADD COLUMN contact_email_sent_at TIMESTAMP NULL;

ALTER TABLE space_enquiries
    ADD CONSTRAINT chk_space_enquiries_client_channel
        CHECK (client_channel IN ('WEB', 'ANDROID'));

COMMENT ON COLUMN space_enquiries.client_channel IS
    'Immutable after create. From X-ACOMI-CLIENT at enquiry creation. Controls contact delivery.';

COMMENT ON COLUMN space_enquiries.contact_email_sent_at IS
    'Set when owner-contact ENQUIRY_SHARED email is enqueued (WEB delivery). Null for ANDROID in-app delivery.';
