-- Cross-space notification lookups for Global Owner Dashboard

CREATE INDEX IF NOT EXISTS idx_space_notifications_user_category_status_created
    ON space_notifications (user_id, category, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_space_notifications_user_status_created
    ON space_notifications (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_space_notifications_user_space_status
    ON space_notifications (user_id, space_id, status);
