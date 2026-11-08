-- Calendar-month rent generation runs and the notification outbox.
-- Payment rows stay on the existing space_payments unique period index.
-- A failed Firebase send is retried from this outbox and never inserts another payment.

CREATE TABLE IF NOT EXISTS monthly_billing_control (
    id                  SMALLINT PRIMARY KEY,
    enabled_from_month  DATE NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_monthly_billing_control_singleton CHECK (id = 1)
);

INSERT INTO monthly_billing_control (id, enabled_from_month)
SELECT 1, (date_trunc('month', now() AT TIME ZONE 'Asia/Kolkata'))::date
WHERE NOT EXISTS (SELECT 1 FROM monthly_billing_control WHERE id = 1);

CREATE TABLE IF NOT EXISTS monthly_billing_runs (
    id                       UUID PRIMARY KEY,
    space_id                 UUID         NOT NULL,
    billing_month            DATE         NOT NULL,
    business_timezone        VARCHAR(64)  NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    started_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    finished_at              TIMESTAMP WITHOUT TIME ZONE,
    eligible_accommodation   INTEGER      NOT NULL DEFAULT 0,
    eligible_mess            INTEGER      NOT NULL DEFAULT 0,
    payments_created         INTEGER      NOT NULL DEFAULT 0,
    payments_skipped         INTEGER      NOT NULL DEFAULT 0,
    payments_failed          INTEGER      NOT NULL DEFAULT 0,
    notifications_queued     INTEGER      NOT NULL DEFAULT 0,
    skip_summary             VARCHAR(4000),
    error_message            VARCHAR(1000),
    created_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_monthly_billing_runs_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT uq_monthly_billing_runs_space_month UNIQUE (space_id, billing_month),
    CONSTRAINT chk_monthly_billing_runs_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_monthly_billing_runs_month
    ON monthly_billing_runs (billing_month, status);

CREATE TABLE IF NOT EXISTS monthly_billing_notifications (
    id                    UUID PRIMARY KEY,
    space_id              UUID         NOT NULL,
    payment_id            UUID         NOT NULL,
    recipient_member_id   UUID         NOT NULL,
    recipient_user_id     UUID,
    event_type            VARCHAR(64)  NOT NULL,
    billing_month         DATE         NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    attempt_count         INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at       TIMESTAMP WITHOUT TIME ZONE,
    last_error            VARCHAR(500),
    fcm_accepted_at       TIMESTAMP WITHOUT TIME ZONE,
    next_attempt_at       TIMESTAMP WITHOUT TIME ZONE,
    created_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_monthly_billing_notifications_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_monthly_billing_notifications_payment
        FOREIGN KEY (payment_id) REFERENCES space_payments (id),
    CONSTRAINT uq_monthly_billing_notifications_payment_event
        UNIQUE (payment_id, event_type),
    CONSTRAINT chk_monthly_billing_notifications_status
        CHECK (status IN ('PENDING', 'FCM_ACCEPTED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_monthly_billing_notifications_due
    ON monthly_billing_notifications (status, next_attempt_at);
