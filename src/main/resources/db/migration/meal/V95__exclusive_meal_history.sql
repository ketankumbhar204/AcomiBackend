-- Remove history rows for combos/items that were also used on a different meal type.
-- Example: Chicken Thali on a Breakfast draft must not appear in Breakfast history
-- when the same combo is used for Lunch/Dinner.

UPDATE menu_planning_history h
SET is_deleted = TRUE,
    updated_at = NOW()
WHERE h.is_deleted = FALSE
  AND h.entry_type = 'COMBO'
  AND h.combo_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM daily_menu_entries dme
      JOIN daily_menus dm ON dm.id = dme.daily_menu_id
      WHERE dm.space_id = h.space_id
        AND dm.is_deleted = FALSE
        AND dm.meal_type <> h.meal_type
        AND dme.is_available = TRUE
        AND COALESCE(dme.is_extra, FALSE) = FALSE
        AND dme.entry_type = 'COMBO'
        AND dme.combo_id = h.combo_id
  );

UPDATE menu_planning_history h
SET is_deleted = TRUE,
    updated_at = NOW()
WHERE h.is_deleted = FALSE
  AND h.entry_type = 'ITEM'
  AND h.item_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM daily_menu_entries dme
      JOIN daily_menus dm ON dm.id = dme.daily_menu_id
      LEFT JOIN daily_menu_package_items dpi ON dpi.entry_id = dme.id
      WHERE dm.space_id = h.space_id
        AND dm.is_deleted = FALSE
        AND dm.meal_type <> h.meal_type
        AND dme.is_available = TRUE
        AND COALESCE(dme.is_extra, FALSE) = FALSE
        AND (
              (dme.entry_type = 'ITEM' AND dme.item_id = h.item_id)
              OR (
                dme.entry_type = 'PACKAGE'
                AND dpi.item_id = h.item_id
                AND (
                  SELECT COUNT(*) FROM daily_menu_package_items x WHERE x.entry_id = dme.id
                ) = 1
              )
        )
  );
