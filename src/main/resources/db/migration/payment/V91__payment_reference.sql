-- Immutable human-readable payment references (PAY-YYYYMMDD-NNNNNN).
-- Minted once on first payment submission; never regenerated.

ALTER TABLE space_payments
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(32);

ALTER TABLE meal_poll_day_payments
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uq_space_payments_payment_reference
    ON space_payments (payment_reference)
    WHERE payment_reference IS NOT NULL;

-- Not unique: bulk meal proofs share one reference across multiple day rows.
CREATE INDEX IF NOT EXISTS idx_meal_poll_day_payments_payment_reference
    ON meal_poll_day_payments (payment_reference)
    WHERE payment_reference IS NOT NULL;

-- Per-space daily sequence for concurrency-safe reference minting.
CREATE TABLE IF NOT EXISTS payment_reference_counters (
    space_id UUID NOT NULL,
    day DATE NOT NULL,
    last_seq INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_reference_counters PRIMARY KEY (space_id, day),
    CONSTRAINT fk_payment_reference_counters_space
        FOREIGN KEY (space_id) REFERENCES spaces (id)
);

-- Soft backfill: reuse existing human batch codes when present (keeps customer/owner alignment).
UPDATE space_payments
SET payment_reference = payment_batch_id
WHERE payment_reference IS NULL
  AND payment_batch_id IS NOT NULL
  AND payment_batch_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-';

UPDATE meal_poll_day_payments d
SET payment_reference = sp.payment_reference
FROM space_payments sp
WHERE d.payment_reference IS NULL
  AND d.payment_batch_id IS NOT NULL
  AND sp.payment_batch_id = d.payment_batch_id
  AND sp.payment_reference IS NOT NULL;

UPDATE meal_poll_day_payments
SET payment_reference = payment_batch_id
WHERE payment_reference IS NULL
  AND payment_batch_id IS NOT NULL
  AND payment_batch_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-';
