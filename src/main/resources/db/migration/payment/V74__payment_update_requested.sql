ALTER TABLE space_payments
    DROP CONSTRAINT IF EXISTS chk_space_payments_status;

ALTER TABLE space_payments
    ADD CONSTRAINT chk_space_payments_status CHECK (
        payment_status IN (
            'PENDING', 'PROOF_UPLOADED', 'UNDER_REVIEW', 'PAID', 'REJECTED', 'UPDATE_REQUESTED'
        )
    );

ALTER TABLE space_payment_timeline_events
    DROP CONSTRAINT IF EXISTS chk_space_payment_timeline_event_type;

ALTER TABLE space_payment_timeline_events
    ADD CONSTRAINT chk_space_payment_timeline_event_type CHECK (
        event_type IN (
            'CREATED', 'PROOF_UPLOADED', 'UNDER_REVIEW', 'APPROVED',
            'REJECTED', 'RESUBMITTED', 'PAID', 'REFUNDED', 'UPDATE_REQUESTED'
        )
    );
