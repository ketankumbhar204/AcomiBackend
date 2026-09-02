-- Optional secondary owner contact on property/mess leads.
-- Existing rows keep only mobile_number; this column is nullable and is not backfilled.

ALTER TABLE property_registrations
    ADD COLUMN alternate_mobile_number VARCHAR(15);

ALTER TABLE mess_registrations
    ADD COLUMN alternate_mobile_number VARCHAR(15);

ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_alternate_mobile
        CHECK (alternate_mobile_number IS NULL OR alternate_mobile_number ~ '^[6-9][0-9]{9}$');

ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_alternate_mobile
        CHECK (alternate_mobile_number IS NULL OR alternate_mobile_number ~ '^[6-9][0-9]{9}$');
