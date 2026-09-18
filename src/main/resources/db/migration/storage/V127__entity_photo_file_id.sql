-- Singular entity photos (one active fileId per entity).
-- Identity remains stored_files.id. Do not store R2 URLs on domain tables.

ALTER TABLE stored_files DROP CONSTRAINT IF EXISTS chk_stored_files_purpose;

ALTER TABLE stored_files ADD CONSTRAINT chk_stored_files_purpose CHECK (purpose IN (
    'PROFILE_PHOTO',
    'IDENTITY_DOCUMENT',
    'ADDRESS_PROOF',
    'MEMBER_DOCUMENT',
    'PAYMENT_PROOF',
    'MEAL_PAYMENT_PROOF',
    'SUBSCRIPTION_PAYMENT_PROOF',
    'COMPLAINT_ATTACHMENT',
    'BUILDING_PHOTO',
    'FLOOR_PHOTO',
    'UNIT_PHOTO',
    'ROOM_PHOTO',
    'BED_PHOTO',
    'MENU_ITEM_PHOTO',
    'COMBO_PHOTO',
    'SPACE_PHOTO'
));

ALTER TABLE buildings ADD COLUMN photo_file_id UUID;
ALTER TABLE buildings
    ADD CONSTRAINT fk_buildings_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_buildings_photo_file_id ON buildings (photo_file_id);

ALTER TABLE floors ADD COLUMN photo_file_id UUID;
ALTER TABLE floors
    ADD CONSTRAINT fk_floors_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_floors_photo_file_id ON floors (photo_file_id);

ALTER TABLE units ADD COLUMN photo_file_id UUID;
ALTER TABLE units
    ADD CONSTRAINT fk_units_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_units_photo_file_id ON units (photo_file_id);

ALTER TABLE rooms ADD COLUMN photo_file_id UUID;
ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_rooms_photo_file_id ON rooms (photo_file_id);

ALTER TABLE beds ADD COLUMN photo_file_id UUID;
ALTER TABLE beds
    ADD CONSTRAINT fk_beds_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_beds_photo_file_id ON beds (photo_file_id);

ALTER TABLE meal_combos ADD COLUMN photo_file_id UUID;
ALTER TABLE meal_combos
    ADD CONSTRAINT fk_meal_combos_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_meal_combos_photo_file_id ON meal_combos (photo_file_id);

ALTER TABLE space_food_item_settings ADD COLUMN photo_file_id UUID;
ALTER TABLE space_food_item_settings
    ADD CONSTRAINT fk_space_food_item_settings_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_space_food_item_settings_photo_file_id
    ON space_food_item_settings (photo_file_id);

ALTER TABLE spaces ADD COLUMN photo_file_id UUID;
ALTER TABLE spaces
    ADD CONSTRAINT fk_spaces_photo_file
    FOREIGN KEY (photo_file_id) REFERENCES stored_files (id);
CREATE INDEX idx_spaces_photo_file_id ON spaces (photo_file_id);
