-- Space-level recurring billing / GST configuration (defaults preserve pre-tax behavior).

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS tax_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS tax_rate_percent NUMERIC(5, 2);

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS price_tax_mode VARCHAR(20);

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS gstin VARCHAR(20);

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS billing_due_day SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE spaces
    DROP CONSTRAINT IF EXISTS chk_spaces_price_tax_mode;

ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_price_tax_mode
        CHECK (
            price_tax_mode IS NULL
            OR price_tax_mode IN ('EXCLUSIVE', 'INCLUSIVE')
        );

ALTER TABLE spaces
    DROP CONSTRAINT IF EXISTS chk_spaces_billing_due_day;

ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_billing_due_day
        CHECK (billing_due_day >= 1 AND billing_due_day <= 28);

ALTER TABLE spaces
    DROP CONSTRAINT IF EXISTS chk_spaces_tax_rate_percent;

ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_tax_rate_percent
        CHECK (
            tax_rate_percent IS NULL
            OR (tax_rate_percent >= 0 AND tax_rate_percent <= 100)
        );
