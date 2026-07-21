-- Month snapshot tables: read path for Payments summary + members pagination.
-- Rebuilt on POST /payments/sync and after payment mutations. Never required for business writes.

CREATE TABLE space_payment_member_month (
    id                      UUID PRIMARY KEY,
    space_id                UUID           NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    member_id               UUID           NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    month                   VARCHAR(7)     NOT NULL,
    member_name             VARCHAR(200)   NOT NULL,
    expected_charges        NUMERIC(12, 2),
    collected               NUMERIC(12, 2),
    under_review            NUMERIC(12, 2),
    pending                 NUMERIC(12, 2),
    currency_code           VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status                  VARCHAR(30)    NOT NULL,
    meal_billing_type       VARCHAR(30),
    meal_balance_remaining  NUMERIC(12, 2),
    meal_balance_purchased  NUMERIC(12, 2),
    meal_balance_consumed   NUMERIC(12, 2),
    meal_balance_unit       VARCHAR(20),
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_space_payment_member_month UNIQUE (space_id, month, member_id),
    CONSTRAINT chk_space_payment_member_month_status CHECK (
        status IN (
            'PAID', 'PARTIAL', 'PENDING', 'UNDER_REVIEW',
            'UPDATE_REQUESTED', 'REJECTED', 'NONE'
        )
    )
);

CREATE INDEX idx_spmm_space_month_pending
    ON space_payment_member_month (space_id, month, pending DESC NULLS LAST, member_name);
CREATE INDEX idx_spmm_space_month_status
    ON space_payment_member_month (space_id, month, status);
CREATE INDEX idx_spmm_space_month_name
    ON space_payment_member_month (space_id, month, lower(member_name));

CREATE TABLE space_payment_month_summary (
    id                      UUID PRIMARY KEY,
    space_id                UUID           NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    month                   VARCHAR(7)     NOT NULL,
    space_type              VARCHAR(30)    NOT NULL,
    expected_charges        NUMERIC(12, 2),
    collected               NUMERIC(12, 2),
    under_review            NUMERIC(12, 2),
    pending                 NUMERIC(12, 2),
    currency_code           VARCHAR(3)     NOT NULL DEFAULT 'INR',
    financial_source        VARCHAR(30),
    meal_billing_type       VARCHAR(30),
    mixed_meal_billing      BOOLEAN,
    prepaid_meals_remaining NUMERIC(12, 2),
    prepaid_amount_collected NUMERIC(12, 2),
    prepaid_currency_code   VARCHAR(3),
    prepaid_unit            VARCHAR(20),
    pending_members         INT            NOT NULL DEFAULT 0,
    member_count            INT            NOT NULL DEFAULT 0,
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_space_payment_month_summary UNIQUE (space_id, month)
);

CREATE INDEX idx_spms_space_month ON space_payment_month_summary (space_id, month);
