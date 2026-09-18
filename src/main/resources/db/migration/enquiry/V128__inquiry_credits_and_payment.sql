-- Inquiry monetization: daily web free usage, credit wallet/ledger, packages, payment config & purchase requests.
-- Does not modify space_enquiries.

CREATE TABLE IF NOT EXISTS inquiry_credit_packages (
    id              UUID PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    price_amount    NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    credits         INTEGER NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inquiry_credit_packages_price CHECK (price_amount >= 0),
    CONSTRAINT chk_inquiry_credit_packages_credits CHECK (credits > 0)
);

CREATE TABLE IF NOT EXISTS inquiry_payment_config (
    id                  UUID PRIMARY KEY,
    upi_id              VARCHAR(120),
    qr_file_id          UUID,
    whatsapp_number     VARCHAR(20),
    instructions        TEXT,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by_user_id  UUID,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiry_payment_config_qr
        FOREIGN KEY (qr_file_id) REFERENCES stored_files (id)
);

-- Singleton seed row (application treats the first/enabled config as current).
INSERT INTO inquiry_payment_config (id, enabled, instructions)
VALUES ('a1000000-0000-4000-8000-000000000001', FALSE,
        '1. Pay using any UPI app.' || chr(10) ||
        '2. Take a screenshot of your payment.' || chr(10) ||
        '3. Send the screenshot on WhatsApp.' || chr(10) ||
        '4. Submit your payment request with the UTR.')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inquiry_credit_packages (id, name, price_amount, currency, credits, enabled, display_order)
VALUES ('a1000000-0000-4000-8000-000000000010', '30 Inquiry Credits', 9.00, 'INR', 30, TRUE, 0)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS inquiry_credit_wallets (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    available_credits   INTEGER NOT NULL DEFAULT 0,
    lifetime_granted    INTEGER NOT NULL DEFAULT 0,
    lifetime_used       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inquiry_credit_wallets_user UNIQUE (user_id),
    CONSTRAINT fk_inquiry_credit_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_inquiry_credit_wallets_available CHECK (available_credits >= 0),
    CONSTRAINT chk_inquiry_credit_wallets_granted CHECK (lifetime_granted >= 0),
    CONSTRAINT chk_inquiry_credit_wallets_used CHECK (lifetime_used >= 0)
);

CREATE TABLE IF NOT EXISTS inquiry_credit_ledger (
    id                  UUID PRIMARY KEY,
    wallet_id           UUID NOT NULL,
    user_id             UUID NOT NULL,
    entry_type          VARCHAR(40) NOT NULL,
    credits             INTEGER NOT NULL,
    balance_after       INTEGER NOT NULL,
    reference_type      VARCHAR(40),
    reference_id        UUID,
    description         VARCHAR(500),
    idempotency_key     VARCHAR(120) NOT NULL,
    created_by_user_id  UUID,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiry_credit_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES inquiry_credit_wallets (id),
    CONSTRAINT fk_inquiry_credit_ledger_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_inquiry_credit_ledger_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_inquiry_credit_ledger_credits CHECK (credits <> 0)
);

CREATE INDEX IF NOT EXISTS idx_inquiry_credit_ledger_user_created
    ON inquiry_credit_ledger (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS inquiry_daily_usage (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    usage_date      DATE NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    free_used       INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inquiry_daily_usage_user_date_channel UNIQUE (user_id, usage_date, channel),
    CONSTRAINT fk_inquiry_daily_usage_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_inquiry_daily_usage_free_used CHECK (free_used >= 0)
);

CREATE TABLE IF NOT EXISTS inquiry_credit_purchase_requests (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    package_id              UUID NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    credits                 INTEGER NOT NULL,
    payment_method          VARCHAR(40) NOT NULL DEFAULT 'UPI_MANUAL',
    status                  VARCHAR(20) NOT NULL,
    utr                     VARCHAR(80),
    requested_at            TIMESTAMP NOT NULL,
    verified_at             TIMESTAMP,
    verified_by_user_id     UUID,
    rejection_reason        VARCHAR(500),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiry_purchase_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_inquiry_purchase_package FOREIGN KEY (package_id) REFERENCES inquiry_credit_packages (id),
    CONSTRAINT chk_inquiry_purchase_amount CHECK (amount >= 0),
    CONSTRAINT chk_inquiry_purchase_credits CHECK (credits > 0),
    CONSTRAINT chk_inquiry_purchase_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_inquiry_purchase_status_requested
    ON inquiry_credit_purchase_requests (status, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_inquiry_purchase_user_status
    ON inquiry_credit_purchase_requests (user_id, status);
