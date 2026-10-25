-- Shared menu library per space owner.
-- Existing row ids, space_id, daily-menu foreign keys, and history rows are left in place.
-- Rollback (manual, after this version is the latest Flyway row):
--   DROP TABLE space_meal_combo_settings;
--   ALTER TABLE meal_combos DROP COLUMN library_listed, DROP COLUMN sample_key, DROP COLUMN owner_id;
--   ALTER TABLE food_items DROP COLUMN owner_id;
--   ALTER TABLE food_categories DROP COLUMN owner_id;
-- Do not delete meal_combos, food_items, or food_categories rows as part of rollback.

ALTER TABLE food_categories
    ADD COLUMN owner_id UUID NULL;

ALTER TABLE food_categories
    ADD CONSTRAINT fk_food_categories_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_food_categories_owner_id ON food_categories (owner_id);

ALTER TABLE food_items
    ADD COLUMN owner_id UUID NULL;

ALTER TABLE food_items
    ADD CONSTRAINT fk_food_items_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_food_items_owner_id ON food_items (owner_id);

ALTER TABLE meal_combos
    ADD COLUMN owner_id UUID NULL;

ALTER TABLE meal_combos
    ADD CONSTRAINT fk_meal_combos_owner FOREIGN KEY (owner_id) REFERENCES users (id);

ALTER TABLE meal_combos
    ADD COLUMN sample_key VARCHAR(40) NULL;

ALTER TABLE meal_combos
    ADD COLUMN library_listed BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_meal_combos_owner_id ON meal_combos (owner_id);

CREATE INDEX idx_meal_combos_owner_listed ON meal_combos (owner_id, library_listed);

UPDATE food_categories AS category
SET owner_id = space.owner_id
FROM spaces AS space
WHERE category.space_id = space.id
  AND category.scope = 'SPACE'
  AND category.owner_id IS NULL;

UPDATE food_items AS item
SET owner_id = space.owner_id
FROM spaces AS space
WHERE item.space_id = space.id
  AND item.scope = 'SPACE'
  AND item.owner_id IS NULL;

UPDATE meal_combos AS combo
SET owner_id = space.owner_id
FROM spaces AS space
WHERE combo.space_id = space.id
  AND combo.owner_id IS NULL;

-- Exact untouched sample copies only. A different price, photo, description, food type,
-- item list, or quantity stays listed and is not merged or deleted.
UPDATE meal_combos AS combo
SET sample_key = defs.sample_key
FROM (
    SELECT
        candidate.id,
        definition.sample_key
    FROM meal_combos AS candidate
    JOIN (
        VALUES
            (
                'standard-lunch-thali',
                'Standard Lunch Thali',
                'Daily lunch combo',
                'VEG',
                ARRAY[
                    '22222222-2222-2222-2222-222222220001:1',
                    '22222222-2222-2222-2222-222222220020:1',
                    '22222222-2222-2222-2222-222222220010:1',
                    '22222222-2222-2222-2222-222222220100:1'
                ]::text[]
            ),
            (
                'dal-rice-combo',
                'Dal Rice Combo',
                'Simple dal rice',
                'VEG',
                ARRAY[
                    '22222222-2222-2222-2222-222222220020:1',
                    '22222222-2222-2222-2222-222222220010:1'
                ]::text[]
            ),
            (
                'chicken-thali',
                'Chicken Thali',
                'Non-veg lunch combo',
                'NON_VEG',
                ARRAY[
                    '22222222-2222-2222-2222-222222220001:1',
                    '22222222-2222-2222-2222-222222220113:1',
                    '22222222-2222-2222-2222-222222220010:1'
                ]::text[]
            ),
            (
                'egg-combo',
                'Egg Combo',
                'Egg meal combo',
                'EGG',
                ARRAY[
                    '22222222-2222-2222-2222-222222220001:1',
                    '22222222-2222-2222-2222-222222220118:1',
                    '22222222-2222-2222-2222-222222220010:1'
                ]::text[]
            )
    ) AS definition(sample_key, name, description, food_type, fingerprint)
        ON candidate.name = definition.name
       AND candidate.description = definition.description
       AND candidate.food_type = definition.food_type
    WHERE candidate.is_active = TRUE
      AND candidate.price IS NULL
      AND candidate.photo_file_id IS NULL
      AND (
          SELECT COALESCE(
              array_agg(
                  item.item_id::text || ':' || item.quantity::text
                  ORDER BY item.sort_order, item.item_id::text
              ),
              ARRAY[]::text[]
          )
          FROM meal_combo_items AS item
          WHERE item.combo_id = candidate.id
      ) = definition.fingerprint
) AS defs
WHERE combo.id = defs.id
  AND combo.sample_key IS NULL;

-- Keep the oldest exact sample copy visible. Later copies stay in the database
-- so daily menus and history that point at them keep working.
WITH ranked AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY owner_id, sample_key
            ORDER BY created_at ASC, id ASC
        ) AS row_number
    FROM meal_combos
    WHERE sample_key IS NOT NULL
      AND is_active = TRUE
      AND owner_id IS NOT NULL
)
UPDATE meal_combos AS combo
SET library_listed = FALSE
FROM ranked
WHERE combo.id = ranked.id
  AND ranked.row_number > 1;

CREATE TABLE space_meal_combo_settings (
    space_id      UUID      NOT NULL,
    meal_combo_id UUID      NOT NULL,
    is_enabled    BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_space_meal_combo_settings PRIMARY KEY (space_id, meal_combo_id),
    CONSTRAINT fk_space_meal_combo_settings_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_space_meal_combo_settings_combo FOREIGN KEY (meal_combo_id) REFERENCES meal_combos (id)
);

CREATE INDEX idx_space_meal_combo_settings_combo ON space_meal_combo_settings (meal_combo_id);
