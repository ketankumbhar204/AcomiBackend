-- Admin source, owner claim tracking, and nullable mobile verification for admin-created leads.

ALTER TABLE property_registrations
    ALTER COLUMN mobile_verified_at DROP NOT NULL;

ALTER TABLE property_registrations
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS claimed_via VARCHAR(40);

ALTER TABLE property_registrations DROP CONSTRAINT IF EXISTS chk_property_registrations_source;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_source
        CHECK (source IN ('PUBLIC_WEBSITE', 'ADMIN'));

ALTER TABLE property_registrations DROP CONSTRAINT IF EXISTS chk_property_registrations_claimed_via;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_claimed_via
        CHECK (claimed_via IS NULL OR claimed_via IN ('PUBLIC_WEBSITE'));

ALTER TABLE mess_registrations
    ALTER COLUMN mobile_verified_at DROP NOT NULL;

ALTER TABLE mess_registrations
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS claimed_via VARCHAR(40);

ALTER TABLE mess_registrations DROP CONSTRAINT IF EXISTS chk_mess_registrations_source;
ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_source
        CHECK (source IN ('PUBLIC_WEBSITE', 'ADMIN'));

ALTER TABLE mess_registrations DROP CONSTRAINT IF EXISTS chk_mess_registrations_claimed_via;
ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_claimed_via
        CHECK (claimed_via IS NULL OR claimed_via IN ('PUBLIC_WEBSITE'));

CREATE INDEX IF NOT EXISTS idx_property_registrations_admin_claim_probe
    ON property_registrations (mobile_number, property_type, source, claimed_at);

CREATE INDEX IF NOT EXISTS idx_mess_registrations_admin_claim_probe
    ON mess_registrations (mobile_number, source, claimed_at);
