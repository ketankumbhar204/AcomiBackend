ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Recurring rent, meal, and security-deposit rows stay unique.
-- Additional member charges (maintenance, utilities, penalties, and so on)
-- can share a type, category, and due date.
ALTER TABLE space_payments DROP CONSTRAINT IF EXISTS uq_space_payments_period;

CREATE UNIQUE INDEX IF NOT EXISTS uq_space_payments_generated_period
    ON space_payments (space_id, member_id, month, payment_type, payment_category, due_date)
    WHERE (payment_type = 'RENT' AND payment_category = 'MONTHLY')
       OR (payment_type = 'MEAL' AND payment_category IN ('MONTHLY', 'DAILY'))
       OR (payment_type = 'DEPOSIT' AND payment_category = 'SECURITY');
