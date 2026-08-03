-- Meal-specific menu planning history (combos + single items reused in the planner).
CREATE TABLE IF NOT EXISTS menu_planning_history (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    meal_type VARCHAR(20) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    combo_id UUID NULL REFERENCES meal_combos(id),
    item_id UUID NULL REFERENCES food_items(id),
    label VARCHAR(150) NOT NULL,
    summary VARCHAR(500),
    food_type VARCHAR(20) NOT NULL DEFAULT 'VEG',
    price NUMERIC(10, 2),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    usage_count INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP NOT NULL,
    last_used_menu_date DATE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_menu_planning_history_entry_type CHECK (entry_type IN ('COMBO', 'ITEM')),
    CONSTRAINT chk_menu_planning_history_refs CHECK (
        (entry_type = 'COMBO' AND combo_id IS NOT NULL AND item_id IS NULL)
        OR (entry_type = 'ITEM' AND item_id IS NOT NULL AND combo_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_menu_planning_history_space_meal_last
    ON menu_planning_history (space_id, meal_type, last_used_at DESC)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_planning_history_combo
    ON menu_planning_history (space_id, meal_type, combo_id)
    WHERE entry_type = 'COMBO' AND combo_id IS NOT NULL AND is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_planning_history_item
    ON menu_planning_history (space_id, meal_type, item_id)
    WHERE entry_type = 'ITEM' AND item_id IS NOT NULL AND is_deleted = FALSE;

-- Backfill COMBO history from existing non-extra daily menu entries.
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
    GROUP BY dm.space_id, dm.meal_type, dme.combo_id
) agg
WHERE NOT EXISTS (
    SELECT 1
    FROM menu_planning_history h
    WHERE h.space_id = agg.space_id
      AND h.meal_type = agg.meal_type
      AND h.entry_type = 'COMBO'
      AND h.combo_id = agg.combo_id
      AND h.is_deleted = FALSE
);

-- Backfill ITEM history from single-item PACKAGE entries.
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
      AND (
          SELECT COUNT(*) FROM daily_menu_package_items x WHERE x.entry_id = dme.id
      ) = 1
    GROUP BY dm.space_id, dm.meal_type, dpi.item_id
) agg
WHERE NOT EXISTS (
    SELECT 1
    FROM menu_planning_history h
    WHERE h.space_id = agg.space_id
      AND h.meal_type = agg.meal_type
      AND h.entry_type = 'ITEM'
      AND h.item_id = agg.item_id
      AND h.is_deleted = FALSE
);
