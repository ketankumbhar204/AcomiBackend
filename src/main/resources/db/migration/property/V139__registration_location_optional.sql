-- A typed location is valid without a Geoapify match, so city, state and pincode
-- may be absent. Existing rows keep their values. Name and address stay required.

ALTER TABLE property_registrations ALTER COLUMN city DROP NOT NULL;
ALTER TABLE property_registrations ALTER COLUMN state DROP NOT NULL;
ALTER TABLE property_registrations ALTER COLUMN pincode DROP NOT NULL;

ALTER TABLE property_registrations DROP CONSTRAINT chk_property_registrations_pincode;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_pincode
        CHECK (pincode IS NULL OR pincode ~ '^[1-9][0-9]{5}$');

ALTER TABLE mess_registrations ALTER COLUMN city DROP NOT NULL;
ALTER TABLE mess_registrations ALTER COLUMN state DROP NOT NULL;
ALTER TABLE mess_registrations ALTER COLUMN pincode DROP NOT NULL;

ALTER TABLE mess_registrations DROP CONSTRAINT chk_mess_registrations_pincode;
ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_pincode
        CHECK (pincode IS NULL OR pincode ~ '^[1-9][0-9]{5}$');
