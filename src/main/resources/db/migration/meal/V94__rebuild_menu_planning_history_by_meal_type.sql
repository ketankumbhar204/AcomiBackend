-- Rebuild menu planning history strictly per space + meal type.
-- Deletes existing rows and re-backfills from daily menus of the same meal type only.

DELETE FROM menu_planning_history;

-- Backfill COMBO history (Breakfast menus → Breakfast rows, etc.).
INSERT INTO menu_planning_history (
    id, space_id, meal_type, entry_type, combo_id, item_id, label, summary, food_type,
    price, currency_code, usage_count, last_used_at, last_used_menu_date, is_deleted, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    agg.space_id,
    agg.meal_type,
    'COMBO',
    agg.combo_id,
    NULL,
    agg.label,
    NULL,
    agg.food_type,
    agg.price,
    agg.currency_code,
    agg.usage_count,
    agg.last_used_at,
    agg.last_used_menu_date,
    FALSE,
    NOW(),
    NOW()
FROM (
    SELECT
        dm.space_id,
        dm.meal_type,
        dme.combo_id,
        COALESCE(NULLIF(TRIM(MAX(dme.label)), ''), MAX(mc.name), 'Combo') AS label,
        COALESCE(MAX(mc.food_type), 'VEG') AS food_type,
        MAX(COALESCE(dme.price, mc.price)) AS price,
        COALESCE(NULLIF(MAX(dme.currency_code), ''), MAX(mc.currency_code), 'INR') AS currency_code,
        COUNT(*)::INTEGER AS usage_count,
        MAX(COALESCE(dm.updated_at, dm.created_at, NOW())) AS last_used_at,
        MAX(dm.menu_date) AS last_used_menu_date
    FROM daily_menu_entries dme
    JOIN daily_menus dm ON dm.id = dme.daily_menu_id
    LEFT JOIN meal_combos mc ON mc.id = dme.combo_id
    WHERE dm.is_deleted = FALSE
      AND dme.is_available = TRUE
      AND COALESCE(dme.is_extra, FALSE) = FALSE
      AND dme.entry_type = 'COMBO'
      AND dme.combo_id IS NOT NULL
      AND dm.meal_type IS NOT NULL
    GROUP BY dm.space_id, dm.meal_type, dme.combo_id
) agg;

-- Backfill ITEM history from single-item PACKAGE entries (same meal type only).
INSERT INTO menu_planning_history (
    id, space_id, meal_type, entry_type, combo_id, item_id, label, summary, food_type,
    price, currency_code, usage_count, last_used_at, last_used_menu_date, is_deleted, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    agg.space_id,
    agg.meal_type,
    'ITEM',
    NULL,
    agg.item_id,
    agg.label,
    agg.summary,
    agg.food_type,
    agg.price,
    agg.currency_code,
    agg.usage_count,
    agg.last_used_at,
    agg.last_used_menu_date,
    FALSE,
    NOW(),
    NOW()
FROM (
    SELECT
        dm.space_id,
        dm.meal_type,
        dpi.item_id,
        COALESCE(NULLIF(TRIM(MAX(dme.label)), ''), MAX(fi.name), 'Item') AS label,
        MAX(fc.name) AS summary,
        COALESCE(MAX(fi.food_type), 'VEG') AS food_type,
        MAX(dme.price) AS price,
        COALESCE(NULLIF(MAX(dme.currency_code), ''), 'INR') AS currency_code,
        COUNT(*)::INTEGER AS usage_count,
        MAX(COALESCE(dm.updated_at, dm.created_at, NOW())) AS last_used_at,
        MAX(dm.menu_date) AS last_used_menu_date
    FROM daily_menu_entries dme
    JOIN daily_menus dm ON dm.id = dme.daily_menu_id
    JOIN daily_menu_package_items dpi ON dpi.entry_id = dme.id
    LEFT JOIN food_items fi ON fi.id = dpi.item_id
    LEFT JOIN food_categories fc ON fc.id = fi.category_id
    WHERE dm.is_deleted = FALSE
      AND dme.is_available = TRUE
      AND COALESCE(dme.is_extra, FALSE) = FALSE
      AND dme.entry_type = 'PACKAGE'
      AND dpi.item_id IS NOT NULL
      AND dm.meal_type IS NOT NULL
      AND (
          SELECT COUNT(*) FROM daily_menu_package_items x WHERE x.entry_id = dme.id
      ) = 1
    GROUP BY dm.space_id, dm.meal_type, dpi.item_id
) agg;
