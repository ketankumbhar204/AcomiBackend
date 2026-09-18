-- Align stored_files.purpose CHECK with FilePurpose enum (adds INQUIRY_PAYMENT_QR).
-- Safe for existing data: only expands allowed values; does not alter rows.

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
    'SPACE_PHOTO',
    'INQUIRY_PAYMENT_QR'
));
