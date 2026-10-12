CREATE TABLE IF NOT EXISTS user_space_favorites (
    id          UUID PRIMARY KEY,
    user_id     UUID      NOT NULL REFERENCES users (id),
    space_id    UUID      NOT NULL REFERENCES spaces (id),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_space_favorites_user_space UNIQUE (user_id, space_id)
);

CREATE INDEX IF NOT EXISTS idx_user_space_favorites_user_created
    ON user_space_favorites (user_id, created_at DESC);
