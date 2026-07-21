ALTER TABLE meal_poll_day_payments
    ADD COLUMN IF NOT EXISTS charged_amount NUMERIC(12, 2);

COMMENT ON COLUMN meal_poll_day_payments.charged_amount IS
    'Current meal total for this member/day. Updated when selections change; Paid edits do not reopen payment.';
