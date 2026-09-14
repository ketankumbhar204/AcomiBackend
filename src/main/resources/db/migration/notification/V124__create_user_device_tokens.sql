CREATE TABLE IF NOT EXISTS user_device_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    fcm_token       TEXT         NOT NULL,
    platform        VARCHAR(20)  NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    app_version     VARCHAR(40),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at    TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_user_device_tokens_platform CHECK (
        platform IN ('ANDROID', 'IOS', 'WEB')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_device_tokens_fcm_token
    ON user_device_tokens (fcm_token);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_device_tokens_device_id
    ON user_device_tokens (device_id);

CREATE INDEX IF NOT EXISTS idx_user_device_tokens_user_active
    ON user_device_tokens (user_id, is_active);
