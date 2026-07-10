CREATE TABLE IF NOT EXISTS space_notifications (
    id                  UUID PRIMARY KEY,
    space_id            UUID         NOT NULL REFERENCES spaces (id),
    organization_id     UUID,
    user_id             UUID         NOT NULL,
    actor_id            UUID,
    entity_type         VARCHAR(40)  NOT NULL,
    entity_id           UUID,
    notification_type   VARCHAR(60)  NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    priority            VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    message             TEXT,
    action_label        VARCHAR(100),
    action_route        VARCHAR(200),
    status              VARCHAR(20)  NOT NULL,
    read_at             TIMESTAMP,
    resolved_at         TIMESTAMP,
    delivery_channels   VARCHAR(100) NOT NULL DEFAULT 'IN_APP',
    dedupe_key          VARCHAR(200) NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT chk_space_notifications_category CHECK (
        category IN ('INFORMATION', 'SUCCESS', 'WARNING', 'ACTION_REQUIRED', 'ERROR')
    ),
    CONSTRAINT chk_space_notifications_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT chk_space_notifications_status CHECK (
        status IN ('UNREAD', 'READ', 'RESOLVED', 'DISMISSED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_space_notifications_open_dedupe
    ON space_notifications (space_id, dedupe_key)
    WHERE status IN ('UNREAD', 'READ');

CREATE INDEX IF NOT EXISTS idx_space_notifications_user_status
    ON space_notifications (space_id, user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_space_notifications_actionable
    ON space_notifications (space_id, user_id, category, status, notification_type);

CREATE INDEX IF NOT EXISTS idx_space_notifications_entity
    ON space_notifications (space_id, entity_type, entity_id, status);
