-- OTP persistence for registration and account-deletion.
-- Do not store plaintext OTP or verification tokens.
-- Existing users keep a null mobile_verified_at; password login is unchanged.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mobile_verified_at TIMESTAMP;

CREATE TABLE auth_otps (
    id                              UUID         NOT NULL,
    mobile_number                   VARCHAR(64)  NOT NULL,
    purpose                         VARCHAR(32)  NOT NULL,
    code_hash                       VARCHAR(64)  NOT NULL,
    expires_at                      TIMESTAMP    NOT NULL,
    attempt_count                   INTEGER      NOT NULL DEFAULT 0,
    max_attempts                    INTEGER      NOT NULL,
    consumed_at                     TIMESTAMP,
    verification_token_hash         VARCHAR(64),
    verification_token_expires_at   TIMESTAMP,
    verification_token_consumed_at  TIMESTAMP,
    request_ip                      VARCHAR(64),
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_auth_otps PRIMARY KEY (id),
    CONSTRAINT chk_auth_otps_purpose CHECK (purpose IN ('REGISTER', 'ACCOUNT_DELETION')),
    CONSTRAINT chk_auth_otps_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_auth_otps_max_attempts CHECK (max_attempts > 0)
);

CREATE INDEX idx_auth_otps_mobile_purpose_created
    ON auth_otps (mobile_number, purpose, created_at DESC);

CREATE INDEX idx_auth_otps_request_ip_created
    ON auth_otps (request_ip, created_at);

CREATE INDEX idx_auth_otps_active_unconsumed
    ON auth_otps (mobile_number, purpose)
    WHERE consumed_at IS NULL;

CREATE UNIQUE INDEX uq_auth_otps_verification_token_hash
    ON auth_otps (verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;
