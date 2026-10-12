-- The PG or mess name and the physical building name can differ.
-- Existing rows leave building_name empty.

ALTER TABLE property_registrations
    ADD COLUMN building_name VARCHAR(150);

ALTER TABLE mess_registrations
    ADD COLUMN building_name VARCHAR(150);
