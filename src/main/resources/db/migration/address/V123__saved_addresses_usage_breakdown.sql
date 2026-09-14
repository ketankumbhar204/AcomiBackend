-- Track property vs mess usage for saved addresses (Admin Addresses Figma metrics).
ALTER TABLE saved_addresses
    ADD COLUMN IF NOT EXISTS property_usage_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS mess_usage_count INTEGER NOT NULL DEFAULT 0;

-- Attribute existing aggregate usage to properties until new typed increments arrive.
UPDATE saved_addresses
SET property_usage_count = usage_count
WHERE usage_count > 0
  AND property_usage_count = 0
  AND mess_usage_count = 0;
