-- Reservation-only booking advance (portion of first-period agreed rent).
-- Null / zero means existing reservation behaviour (no rent rows until move-in).

ALTER TABLE occupancies
    ADD COLUMN booking_advance_amount DECIMAL(12, 2);

ALTER TABLE occupancies
    ADD CONSTRAINT chk_occupancies_booking_advance_amount
        CHECK (booking_advance_amount IS NULL OR booking_advance_amount >= 0);
