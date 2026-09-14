-- Daily payment reminder delivery ledger (channel-agnostic).
-- Idempotency: one attempt row per payment + reminder type + business date + channel.
-- Does not alter space_payments amounts or statuses.

CREATE TABLE IF NOT EXISTS payment_reminder_deliveries (
    id                    UUID PRIMARY KEY,
    space_id              UUID         NOT NULL,
    payment_id            UUID         NOT NULL,
    recipient_member_id   UUID         NOT NULL,
    recipient_user_id     UUID,
    recipient_mobile      VARCHAR(32),
    reminder_type         VARCHAR(40)  NOT NULL,
    channel               VARCHAR(20)  NOT NULL,
    business_date         DATE         NOT NULL,
    delivery_status       VARCHAR(20)  NOT NULL,
    outstanding_amount    NUMERIC(12, 2),
    currency_code         VARCHAR(3),
    days_overdue          INTEGER,
    provider_message_id   VARCHAR(200),
    failure_reason        VARCHAR(500),
    attempt_count         INTEGER      NOT NULL DEFAULT 1,
    last_attempt_at       TIMESTAMP WITHOUT TIME ZONE,
    sent_at               TIMESTAMP WITHOUT TIME ZONE,
    created_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_payment_reminder_deliveries_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_payment_reminder_deliveries_payment
        FOREIGN KEY (payment_id) REFERENCES space_payments (id),
    CONSTRAINT chk_payment_reminder_deliveries_type
        CHECK (reminder_type IN (
            'RENT_PAYMENT_OVERDUE',
            'MEAL_PAYMENT_OVERDUE',
            'GENERIC_PAYMENT_OVERDUE'
        )),
    CONSTRAINT chk_payment_reminder_deliveries_channel
        CHECK (channel IN ('WHATSAPP', 'IN_APP', 'SMS', 'EMAIL', 'PUSH')),
    CONSTRAINT chk_payment_reminder_deliveries_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT uq_payment_reminder_delivery_day
        UNIQUE (payment_id, reminder_type, business_date, channel)
);

CREATE INDEX IF NOT EXISTS idx_payment_reminder_deliveries_space_date
    ON payment_reminder_deliveries (space_id, business_date);

CREATE INDEX IF NOT EXISTS idx_payment_reminder_deliveries_payment
    ON payment_reminder_deliveries (payment_id, business_date DESC);

CREATE INDEX IF NOT EXISTS idx_payment_reminder_deliveries_status
    ON payment_reminder_deliveries (delivery_status, last_attempt_at);

-- Allow timeline visibility for reminder attempts without changing payment amounts.
ALTER TABLE space_payment_timeline_events
    DROP CONSTRAINT IF EXISTS chk_space_payment_timeline_event_type;

ALTER TABLE space_payment_timeline_events
    ADD CONSTRAINT chk_space_payment_timeline_event_type CHECK (
        event_type IN (
            'CREATED', 'PROOF_UPLOADED', 'UNDER_REVIEW', 'APPROVED',
            'REJECTED', 'RESUBMITTED', 'PAID', 'REFUNDED', 'UPDATE_REQUESTED',
            'REMINDER_SENT', 'REMINDER_FAILED'
        )
    );
