-- Immutable calculation snapshots for generated monthly obligations.
-- Existing rows: treat as no-tax; amounts unchanged.

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS billing_period_start DATE;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS billing_period_end DATE;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS billable_days INTEGER;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS days_in_month INTEGER;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS configured_monthly_amount NUMERIC(12, 2);

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS is_prorated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS tax_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS tax_rate_percent NUMERIC(5, 2);

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS price_tax_mode VARCHAR(20);

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS base_amount NUMERIC(12, 2);

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12, 2);

-- Backfill historical rows without changing amount.
UPDATE space_payments
SET base_amount = amount,
    tax_amount = 0,
    tax_enabled = FALSE,
    is_prorated = FALSE
WHERE base_amount IS NULL;

ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS chk_space_payments_price_tax_mode;

ALTER TABLE space_payments
    ADD CONSTRAINT chk_space_payments_price_tax_mode
        CHECK (
            price_tax_mode IS NULL
            OR price_tax_mode IN ('EXCLUSIVE', 'INCLUSIVE')
        );
