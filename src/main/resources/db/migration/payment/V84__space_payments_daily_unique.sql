-- Allow multiple daily meal proofs per member/month (unique on due_date too).
-- MONTHLY rent/meal expected rows keep a single due_date (month end) so uniqueness holds.

ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS uq_space_payments_period;

ALTER TABLE space_payments
    ADD CONSTRAINT uq_space_payments_period
        UNIQUE (space_id, member_id, month, payment_type, payment_category, due_date);

CREATE INDEX IF NOT EXISTS idx_space_payments_due_date
    ON space_payments (space_id, due_date);
