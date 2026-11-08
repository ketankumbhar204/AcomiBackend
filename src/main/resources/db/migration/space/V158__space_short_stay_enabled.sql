-- Short Stay stays hidden until an owner turns it on for that space.
-- Existing spaces stay off. Do not backfill true.
ALTER TABLE spaces
    ADD COLUMN short_stay_enabled BOOLEAN NOT NULL DEFAULT FALSE;
