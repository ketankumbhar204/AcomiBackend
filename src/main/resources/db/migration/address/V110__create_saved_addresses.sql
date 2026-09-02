-- Reusable saved addresses for admin property/mess lead forms.
-- Existing registration rows keep their own address snapshot; this table is a library only.

CREATE TABLE saved_addresses (
    id                      UUID            NOT NULL,
    created_by_user_id      UUID            NOT NULL,
    address_line            VARCHAR(255)    NOT NULL,
    city                    VARCHAR(80)     NOT NULL,
    state                   VARCHAR(80)     NOT NULL,
    pincode                 VARCHAR(6)      NOT NULL,
    map_url                 VARCHAR(512),
    fingerprint             VARCHAR(64)     NOT NULL,
    usage_count             INTEGER         NOT NULL DEFAULT 0,
    last_used_at            TIMESTAMP,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_saved_addresses PRIMARY KEY (id),
    CONSTRAINT fk_saved_addresses_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_saved_addresses_usage_count CHECK (usage_count >= 0)
);

CREATE UNIQUE INDEX uk_saved_addresses_owner_fingerprint_active
    ON saved_addresses (created_by_user_id, fingerprint)
    WHERE is_active = TRUE;

CREATE INDEX idx_saved_addresses_owner_recent
    ON saved_addresses (created_by_user_id, last_used_at DESC, created_at DESC);

CREATE INDEX idx_saved_addresses_owner_city
    ON saved_addresses (created_by_user_id, city);

CREATE INDEX idx_saved_addresses_owner_pincode
    ON saved_addresses (created_by_user_id, pincode);
