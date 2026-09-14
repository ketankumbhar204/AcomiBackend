-- Enrich property and mess registrations for bulk import review → convert-to-space.

-- Property registrations
ALTER TABLE property_registrations
    ADD COLUMN IF NOT EXISTS additional_mobile_number VARCHAR(15),
    ADD COLUMN IF NOT EXISTS sharing_notes TEXT,
    ADD COLUMN IF NOT EXISTS food_included_listing BOOLEAN,
    ADD COLUMN IF NOT EXISTS gender_policy VARCHAR(20),
    ADD COLUMN IF NOT EXISTS unmapped_amenities TEXT,
    ADD COLUMN IF NOT EXISTS linked_owner_user_id UUID;

ALTER TABLE property_registrations
    DROP CONSTRAINT IF EXISTS fk_property_registrations_linked_owner;

ALTER TABLE property_registrations
    ADD CONSTRAINT fk_property_registrations_linked_owner
        FOREIGN KEY (linked_owner_user_id) REFERENCES users (id);

ALTER TABLE property_registrations
    DROP CONSTRAINT IF EXISTS chk_property_registrations_gender_policy;

ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_gender_policy
        CHECK (gender_policy IS NULL OR gender_policy IN ('MALE', 'FEMALE', 'MIXED'));

CREATE INDEX IF NOT EXISTS idx_property_registrations_linked_owner
    ON property_registrations (linked_owner_user_id);

-- Mess registrations
ALTER TABLE mess_registrations
    ADD COLUMN IF NOT EXISTS additional_mobile_number VARCHAR(15),
    ADD COLUMN IF NOT EXISTS sharing_notes TEXT,
    ADD COLUMN IF NOT EXISTS food_included_listing BOOLEAN,
    ADD COLUMN IF NOT EXISTS gender_policy VARCHAR(20),
    ADD COLUMN IF NOT EXISTS unmapped_amenities TEXT,
    ADD COLUMN IF NOT EXISTS linked_owner_user_id UUID;

ALTER TABLE mess_registrations
    DROP CONSTRAINT IF EXISTS fk_mess_registrations_linked_owner;

ALTER TABLE mess_registrations
    ADD CONSTRAINT fk_mess_registrations_linked_owner
        FOREIGN KEY (linked_owner_user_id) REFERENCES users (id);

ALTER TABLE mess_registrations
    DROP CONSTRAINT IF EXISTS chk_mess_registrations_gender_policy;

ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_gender_policy
        CHECK (gender_policy IS NULL OR gender_policy IN ('MALE', 'FEMALE', 'MIXED'));

CREATE INDEX IF NOT EXISTS idx_mess_registrations_linked_owner
    ON mess_registrations (linked_owner_user_id);
