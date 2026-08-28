-- Optional admin metadata: marks leads created for testing/demo purposes.

ALTER TABLE property_registrations
    ADD COLUMN IF NOT EXISTS test_lead BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mess_registrations
    ADD COLUMN IF NOT EXISTS test_lead BOOLEAN NOT NULL DEFAULT FALSE;
