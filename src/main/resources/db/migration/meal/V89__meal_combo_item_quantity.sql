-- Optional per-item quantity inside a combo (informational; does not affect price).
-- Existing rows default to 1.
ALTER TABLE meal_combo_items
    ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 1;

ALTER TABLE meal_combo_items
    DROP CONSTRAINT IF EXISTS chk_meal_combo_items_quantity;

ALTER TABLE meal_combo_items
    ADD CONSTRAINT chk_meal_combo_items_quantity CHECK (quantity >= 1);
