-- Manual charges use payment types and categories that the Java enums already allow.
-- The original checks only listed the first rent/meal/deposit set, so UTILITY,
-- SERVICE, PENALTY, and GAS were rejected on insert.

ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS chk_space_payments_type;

ALTER TABLE space_payments
    ADD CONSTRAINT chk_space_payments_type CHECK (
        payment_type IN (
            'MEAL', 'RENT', 'DEPOSIT', 'MAINTENANCE',
            'UTILITY', 'SERVICE', 'PENALTY', 'OTHER'
        )
    );

ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS chk_space_payments_category;

ALTER TABLE space_payments
    ADD CONSTRAINT chk_space_payments_category CHECK (
        payment_category IN (
            'MONTHLY', 'DAILY', 'EXTRA', 'ADVANCE', 'SECURITY',
            'REFUND', 'ELECTRICITY', 'WATER', 'GAS', 'INTERNET', 'OTHER'
        )
    );
