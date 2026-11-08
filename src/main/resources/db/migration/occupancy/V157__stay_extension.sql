-- Stay extension support. Not applied by this change.
-- prior_rent_snapshot and rent_effective_month keep an earlier monthly price
-- when rent changes from a selected month. History stores the idempotency key
-- because a free extension has no payment row. The extra-period index stops
-- two RENT/EXTRA rows for the same half-open interval. No backfill.

ALTER TABLE occupancies
    ADD COLUMN prior_rent_snapshot DECIMAL(12, 2),
    ADD COLUMN rent_effective_month DATE;

ALTER TABLE occupancies
    ADD CONSTRAINT chk_occupancies_prior_rent_snapshot
        CHECK (prior_rent_snapshot IS NULL OR prior_rent_snapshot >= 0);

ALTER TABLE occupancy_history
    ADD COLUMN idempotency_key VARCHAR(64),
    ADD COLUMN extension_charge_id UUID;

CREATE UNIQUE INDEX uq_occupancy_history_extension_key
    ON occupancy_history (occupancy_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uq_space_payments_rent_extra_period
    ON space_payments (occupancy_id, billing_period_start, billing_period_end)
    WHERE payment_type = 'RENT'
      AND payment_category = 'EXTRA'
      AND occupancy_id IS NOT NULL
      AND billing_period_start IS NOT NULL
      AND billing_period_end IS NOT NULL;
