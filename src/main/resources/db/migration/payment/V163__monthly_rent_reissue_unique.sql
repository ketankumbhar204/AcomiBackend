-- A cancelled or fully refunded monthly rent row stays in place.
-- An explicit reissue may insert a new open row for the same period.
-- Meal and security-deposit uniqueness is unchanged.

DROP INDEX IF EXISTS uq_space_payments_generated_period;

CREATE UNIQUE INDEX uq_space_payments_generated_period
    ON space_payments (space_id, member_id, month, payment_type, payment_category, due_date)
    WHERE (
            (payment_type = 'RENT'
                AND payment_category = 'MONTHLY'
                AND payment_status NOT IN ('CANCELLED', 'REFUNDED'))
         OR (payment_type = 'MEAL' AND payment_category IN ('MONTHLY', 'DAILY'))
         OR (payment_type = 'DEPOSIT' AND payment_category = 'SECURITY')
          );
