-- One active contact enquiry per (space, requester).
-- Active = PENDING or SHARED. REJECTED / EXPIRED / CANCELLED remain historical
-- and may be followed by a new enquiry.
--
-- Does not delete or rewrite existing rows. If duplicate active rows already
-- exist, this script fails so they can be reviewed before the index is applied.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM space_enquiries
        WHERE status IN ('PENDING', 'SHARED')
        GROUP BY space_id, requester_user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create uq_space_enquiries_active_requester_space: duplicate PENDING/SHARED enquiries exist for the same (space_id, requester_user_id). Resolve those rows before applying this migration.';
    END IF;
END $$;

DROP INDEX IF EXISTS uq_space_enquiries_pending_requester_space;

CREATE UNIQUE INDEX uq_space_enquiries_active_requester_space
    ON space_enquiries (space_id, requester_user_id)
    WHERE status IN ('PENDING', 'SHARED');
