-- Payment cancellation + collected-payment settlement (refund / retain).
-- Original obligations stay immutable for amount/collection history.
-- Refunds are separate REFUND-category payment rows linked via related_payment_id.

ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS chk_space_payments_status;

ALTER TABLE space_payments
    ADD CONSTRAINT chk_space_payments_status CHECK (
        payment_status IN (
            'PENDING',
            'PROOF_UPLOADED',
            'UNDER_REVIEW',
            'PAID',
            'REJECTED',
            'UPDATE_REQUESTED',
            'CANCELLED'
        )
    );

ALTER TABLE space_payment_timeline_events
    DROP CONSTRAINT IF EXISTS chk_space_payment_timeline_event_type;

ALTER TABLE space_payment_timeline_events
    ADD CONSTRAINT chk_space_payment_timeline_event_type CHECK (
        event_type IN (
            'CREATED',
            'PROOF_UPLOADED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'RESUBMITTED',
            'PAID',
            'REFUNDED',
            'UPDATE_REQUESTED',
            'CANCELLED',
            'RETAINED',
            'REMINDER_SENT',
            'REMINDER_FAILED'
        )
    );

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS related_payment_id UUID REFERENCES space_payments (id);

CREATE INDEX IF NOT EXISTS idx_space_payments_related_payment
    ON space_payments (related_payment_id);

CREATE TABLE IF NOT EXISTS payment_settlements (
    id                  UUID PRIMARY KEY,
    space_id            UUID         NOT NULL REFERENCES spaces (id),
    payment_id          UUID         NOT NULL REFERENCES space_payments (id),
    occupancy_id        UUID         REFERENCES occupancies (id),
    action              VARCHAR(30)  NOT NULL,
    refund_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    retained_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reason              TEXT,
    refund_payment_id   UUID         REFERENCES space_payments (id),
    performed_by        UUID,
    performed_at        TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_settlements_action CHECK (
        action IN ('CANCEL_UNPAID', 'FULL_REFUND', 'PARTIAL_REFUND', 'RETAIN')
    ),
    CONSTRAINT chk_payment_settlements_amounts CHECK (
        refund_amount >= 0 AND retained_amount >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_settlements_payment
    ON payment_settlements (payment_id);

CREATE INDEX IF NOT EXISTS idx_payment_settlements_occupancy
    ON payment_settlements (occupancy_id);

CREATE INDEX IF NOT EXISTS idx_payment_settlements_space
    ON payment_settlements (space_id);
