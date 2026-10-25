CREATE TABLE IF NOT EXISTS admin_notification_test_attempts (
    id                  UUID PRIMARY KEY,
    admin_user_id       UUID         NOT NULL REFERENCES users (id),
    notification_type   VARCHAR(64)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    targeted_count      INT          NOT NULL,
    accepted_count      INT          NOT NULL,
    failed_count        INT          NOT NULL,
    error_code          VARCHAR(64),
    error_detail        VARCHAR(200),
    client_request_id   VARCHAR(64),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_notification_tests_admin_created
    ON admin_notification_test_attempts (admin_user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_notification_test_request
    ON admin_notification_test_attempts (admin_user_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
