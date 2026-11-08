-- Add REFUNDED workflow status and denormalized refund/retain totals on obligations.
-- Partial refunds stay PAID with refunded_amount > 0; only full cash return → REFUNDED.

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
            'CANCELLED',
            'REFUNDED'
        )
    );

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS retained_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE payment_settlements
    ADD COLUMN IF NOT EXISTS refund_method VARCHAR(20);

ALTER TABLE payment_settlements
    ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(100);

ALTER TABLE payment_settlements
    ADD COLUMN IF NOT EXISTS refund_date DATE;

ALTER TABLE payment_settlements
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE payment_settlements
    DROP CONSTRAINT IF EXISTS chk_payment_settlements_processing_status;

ALTER TABLE payment_settlements
    ADD CONSTRAINT chk_payment_settlements_processing_status CHECK (
        processing_status IN ('COMPLETED', 'FAILED', 'PENDING')
    );
