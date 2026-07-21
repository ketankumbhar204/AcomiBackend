ALTER TABLE meal_poll_day_payments
    ADD COLUMN IF NOT EXISTS payment_batch_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_meal_poll_day_payments_batch_id
    ON meal_poll_day_payments (payment_batch_id)
    WHERE payment_batch_id IS NOT NULL;
