-- Excel import may omit contact, address, and rent. Space type and name stay required.
-- Existing rows are unchanged. Public registration still requires these fields in the API.

ALTER TABLE property_registrations ALTER COLUMN mobile_number DROP NOT NULL;
ALTER TABLE property_registrations ALTER COLUMN address_line DROP NOT NULL;
ALTER TABLE property_registrations ALTER COLUMN starting_price DROP NOT NULL;
ALTER TABLE property_registrations ALTER COLUMN price_basis DROP NOT NULL;

ALTER TABLE mess_registrations ALTER COLUMN mobile_number DROP NOT NULL;
ALTER TABLE mess_registrations ALTER COLUMN address_line DROP NOT NULL;
ALTER TABLE mess_registrations ALTER COLUMN monthly_price DROP NOT NULL;
