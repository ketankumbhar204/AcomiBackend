-- History is for meal mains (combos / non-extra items). Catalog extras belong in Extras section only.
UPDATE menu_planning_history h
SET is_deleted = TRUE,
    updated_at = NOW()
WHERE h.is_deleted = FALSE
  AND h.entry_type = 'ITEM'
  AND h.item_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM space_food_item_settings s
      WHERE s.space_id = h.space_id
        AND s.item_id = h.item_id
        AND s.is_extra = TRUE
  );
