-- Mess meal extras: same catalog items can appear as main dishes and as add-ons.
ALTER TABLE daily_menu_entries
    ADD COLUMN IF NOT EXISTS is_extra BOOLEAN NOT NULL DEFAULT FALSE;
