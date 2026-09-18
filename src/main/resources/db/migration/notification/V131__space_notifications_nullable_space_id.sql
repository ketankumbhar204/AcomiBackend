-- Platform / user-level notifications (e.g. inquiry-credit purchase) are not tied to a Space.
-- Allow NULL space_id instead of inventing a sentinel space. FK still applies when set.
ALTER TABLE space_notifications
    ALTER COLUMN space_id DROP NOT NULL;

-- Harden dedupe for platform notifications (NULL space_id rows).
CREATE UNIQUE INDEX IF NOT EXISTS uq_space_notifications_platform_open_dedupe
    ON space_notifications (dedupe_key)
    WHERE space_id IS NULL AND status IN ('UNREAD', 'READ');
