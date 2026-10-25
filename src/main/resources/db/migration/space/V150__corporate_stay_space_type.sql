-- Corporate Stay is a property accommodation type. Mess stays on its own registration table.
-- Existing rows are unchanged. No backfill from Rental.

ALTER TABLE spaces DROP CONSTRAINT IF EXISTS chk_spaces_type;
ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_type
        CHECK (type IN ('PG', 'MESS', 'HOSTEL', 'CO_LIVING', 'RENTAL', 'CORPORATE_STAY'));

ALTER TABLE property_registrations DROP CONSTRAINT IF EXISTS chk_property_registrations_type;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_type
        CHECK (property_type IN ('PG', 'HOSTEL', 'CO_LIVING', 'RENTAL', 'CORPORATE_STAY'));
