ALTER TABLE space_food_item_settings
    ADD COLUMN default_price NUMERIC(10, 2) NULL,
    ADD COLUMN currency_code VARCHAR(3) NULL DEFAULT 'INR';
