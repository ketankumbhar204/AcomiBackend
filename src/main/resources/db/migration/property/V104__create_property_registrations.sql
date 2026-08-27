-- Public website property-registration leads.
--
-- These are NOT operational spaces. A row here is an unauthenticated submission that ACOMI
-- staff review and may later convert into a real space (tracked via converted_space_id).
-- No user account is created for a lead, so there is no owner_id FK to users.

CREATE SEQUENCE property_registration_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE property_registrations (
    id                      UUID            NOT NULL,
    reference               VARCHAR(20)     NOT NULL,
    property_type           VARCHAR(20)     NOT NULL,
    property_name           VARCHAR(150)    NOT NULL,
    owner_name              VARCHAR(120)    NOT NULL,
    mobile_number           VARCHAR(15)     NOT NULL,
    mobile_verified_at      TIMESTAMP       NOT NULL,
    description             TEXT,
    address_line            VARCHAR(255)    NOT NULL,
    city                    VARCHAR(80)     NOT NULL,
    state                   VARCHAR(80)     NOT NULL,
    pincode                 VARCHAR(6)      NOT NULL,
    -- Reserved so a future map picker needs no migration. No maps integration exists today.
    latitude                NUMERIC(10, 7),
    longitude               NUMERIC(10, 7),
    map_url                 VARCHAR(512),
    starting_price          NUMERIC(12, 2)  NOT NULL,
    price_basis             VARCHAR(12)     NOT NULL,
    capacity_estimate       INTEGER,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    source                  VARCHAR(40)     NOT NULL DEFAULT 'PUBLIC_WEBSITE',
    converted_space_id      UUID,
    reviewed_by             UUID,
    reviewed_at             TIMESTAMP,
    review_notes            TEXT,
    request_ip              VARCHAR(64),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_property_registrations PRIMARY KEY (id),
    CONSTRAINT uq_property_registrations_reference UNIQUE (reference),
    CONSTRAINT fk_property_registrations_converted_space
        FOREIGN KEY (converted_space_id) REFERENCES spaces (id),
    CONSTRAINT fk_property_registrations_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id),
    -- MESS is intentionally excluded: mess/food-service registration is a separate flow.
    CONSTRAINT chk_property_registrations_type
        CHECK (property_type IN ('PG', 'HOSTEL', 'CO_LIVING', 'RENTAL')),
    CONSTRAINT chk_property_registrations_status
        CHECK (status IN ('PENDING', 'IN_REVIEW', 'CONTACTED', 'CONVERTED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT chk_property_registrations_price_basis
        CHECK (price_basis IN ('PER_BED', 'PER_ROOM', 'PER_UNIT')),
    CONSTRAINT chk_property_registrations_source
        CHECK (source IN ('PUBLIC_WEBSITE')),
    CONSTRAINT chk_property_registrations_pincode
        CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT chk_property_registrations_starting_price
        CHECK (starting_price >= 0),
    CONSTRAINT chk_property_registrations_capacity
        CHECK (capacity_estimate IS NULL OR capacity_estimate >= 0)
);

CREATE INDEX idx_property_registrations_status_created
    ON property_registrations (status, created_at DESC);

CREATE INDEX idx_property_registrations_mobile
    ON property_registrations (mobile_number);

-- Supports soft duplicate detection. One owner may legitimately register many properties,
-- so this is deliberately an index and not a unique constraint.
CREATE INDEX idx_property_registrations_duplicate_probe
    ON property_registrations (mobile_number, pincode, created_at DESC);

CREATE TABLE property_registration_amenities (
    id                          UUID            NOT NULL,
    property_registration_id    UUID            NOT NULL,
    amenity_code                VARCHAR(50)     NOT NULL,
    custom_label                VARCHAR(120),
    display_order               INT             NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_property_registration_amenities PRIMARY KEY (id),
    CONSTRAINT fk_property_registration_amenities_registration
        FOREIGN KEY (property_registration_id) REFERENCES property_registrations (id) ON DELETE CASCADE,
    CONSTRAINT chk_property_registration_amenities_custom_label
        CHECK (amenity_code <> 'CUSTOM' OR (custom_label IS NOT NULL AND LENGTH(TRIM(custom_label)) > 0))
);

CREATE INDEX idx_property_registration_amenities_registration_id
    ON property_registration_amenities (property_registration_id);
