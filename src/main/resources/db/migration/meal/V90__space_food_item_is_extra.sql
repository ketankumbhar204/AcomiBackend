-- Mess Menu Library: mark reusable catalog items as extras (per space).
ALTER TABLE space_food_item_settings
    ADD COLUMN IF NOT EXISTS is_extra BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill from temporary daily-menu extras so existing Mess data stays usable.
INSERT INTO space_food_item_settings (space_id, item_id, is_enabled, default_price, currency_code, updated_at, is_extra)
SELECT DISTINCT
    m.space_id,
    pi.item_id,
    TRUE,
    CAST(NULL AS NUMERIC(10, 2)),
    'INR',
    NOW(),
    TRUE
FROM daily_menu_entries e
JOIN daily_menus m ON m.id = e.daily_menu_id
JOIN daily_menu_package_items pi ON pi.entry_id = e.id
WHERE e.is_extra = TRUE
  AND m.is_deleted = FALSE
ON CONFLICT (space_id, item_id) DO UPDATE
SET is_extra = TRUE,
    updated_at = NOW();
