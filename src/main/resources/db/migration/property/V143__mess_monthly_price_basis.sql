ALTER TABLE mess_registrations
    ADD COLUMN monthly_price_basis VARCHAR(12) NULL;

ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_monthly_price_basis
        CHECK (monthly_price_basis IS NULL OR monthly_price_basis IN ('PER_BED', 'PER_ROOM', 'PER_UNIT'));
