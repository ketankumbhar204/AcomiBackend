-- Gate Find-a-Place discovery on discoverable; store coords on spaces.

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS discoverable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

CREATE INDEX IF NOT EXISTS idx_spaces_active_discoverable_created
    ON spaces (is_active, discoverable, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_spaces_active_discoverable_name_lower
    ON spaces (is_active, discoverable, lower(name));
