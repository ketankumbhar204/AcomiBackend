-- Link bulk meal proofs to a single space_payments row (shared payment_batch_id).
ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS payment_batch_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_space_payments_batch
    ON space_payments (space_id, payment_batch_id)
    WHERE payment_batch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_space_payments_batch
    ON space_payments (payment_batch_id)
    WHERE payment_batch_id IS NOT NULL;
