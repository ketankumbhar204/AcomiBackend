-- Nullable stay identity. Existing rows stay null and remain monthly. No backfill.
-- pricing_basis has no allowed-value list until that decision is approved.

ALTER TABLE occupancies
    ADD COLUMN stay_kind VARCHAR(20),
    ADD COLUMN pricing_basis VARCHAR(30),
    ADD COLUMN agreed_total DECIMAL(12, 2);

ALTER TABLE occupancies
    ADD CONSTRAINT chk_occupancies_stay_kind
        CHECK (stay_kind IS NULL OR stay_kind IN ('MONTHLY', 'SHORT', 'OPEN_ENDED'));

ALTER TABLE occupancies
    ADD CONSTRAINT chk_occupancies_agreed_total
        CHECK (agreed_total IS NULL OR agreed_total >= 0);

ALTER TABLE occupancy_history
    DROP CONSTRAINT chk_occupancy_history_event_type;

ALTER TABLE occupancy_history
    ADD CONSTRAINT chk_occupancy_history_event_type
        CHECK (event_type IN (
            'ALLOCATED', 'TRANSFERRED', 'VACATED',
            'RESERVED', 'MOVE_IN', 'RESERVATION_CANCELLED',
            'EXTENDED'
        ));
