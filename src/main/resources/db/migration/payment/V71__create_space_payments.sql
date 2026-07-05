CREATE TABLE space_payments (
    id                UUID PRIMARY KEY,
    space_id          UUID         NOT NULL REFERENCES spaces (id),
    member_id         UUID         NOT NULL REFERENCES members (id),
    occupancy_id      UUID         REFERENCES occupancies (id),
    payment_type      VARCHAR(20)  NOT NULL,
    payment_category  VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    currency_code     VARCHAR(3)   NOT NULL DEFAULT 'INR',
    due_date          DATE         NOT NULL,
    month             VARCHAR(7)   NOT NULL,
    payment_method    VARCHAR(20),
    payment_status    VARCHAR(20)  NOT NULL,
    proof_url         TEXT,
    reference_number  VARCHAR(100),
    remarks           TEXT,
    rejection_reason  TEXT,
    rejection_code    VARCHAR(40),
    reviewed_by       UUID,
    reviewed_at       TIMESTAMP,
    payment_date      DATE,
    target_label      VARCHAR(200),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_space_payments_period UNIQUE (space_id, member_id, month, payment_type, payment_category),
    CONSTRAINT chk_space_payments_type CHECK (
        payment_type IN ('MEAL', 'RENT', 'DEPOSIT', 'MAINTENANCE', 'OTHER')
    ),
    CONSTRAINT chk_space_payments_category CHECK (
        payment_category IN (
            'MONTHLY', 'DAILY', 'EXTRA', 'ADVANCE', 'SECURITY',
            'REFUND', 'ELECTRICITY', 'WATER', 'INTERNET', 'OTHER'
        )
    ),
    CONSTRAINT chk_space_payments_status CHECK (
        payment_status IN ('PENDING', 'PROOF_UPLOADED', 'UNDER_REVIEW', 'PAID', 'REJECTED')
    )
);

CREATE INDEX idx_space_payments_space_month ON space_payments (space_id, month);
CREATE INDEX idx_space_payments_member ON space_payments (member_id);
CREATE INDEX idx_space_payments_status ON space_payments (payment_status);

CREATE TABLE space_payment_timeline_events (
    id            UUID PRIMARY KEY,
    payment_id    UUID        NOT NULL REFERENCES space_payments (id) ON DELETE CASCADE,
    event_type    VARCHAR(20) NOT NULL,
    performed_at  TIMESTAMP   NOT NULL,
    remarks       TEXT,
    performed_by  UUID,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_space_payment_timeline_event_type CHECK (
        event_type IN (
            'CREATED', 'PROOF_UPLOADED', 'UNDER_REVIEW', 'APPROVED',
            'REJECTED', 'RESUBMITTED', 'PAID', 'REFUNDED'
        )
    )
);

CREATE INDEX idx_space_payment_timeline_payment ON space_payment_timeline_events (payment_id);
