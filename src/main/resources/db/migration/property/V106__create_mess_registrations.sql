-- Public website mess-registration leads.
--
-- These are NOT operational mess spaces. A row here is an unauthenticated submission
-- that ACOMI staff review and may later convert into a real space (converted_space_id).
-- No user account is created for a lead.

CREATE SEQUENCE mess_registration_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE mess_registrations (
    id                      UUID            NOT NULL,
    reference               VARCHAR(20)     NOT NULL,
    mess_name               VARCHAR(150)    NOT NULL,
    owner_name              VARCHAR(120)     NOT NULL,
    mobile_number           VARCHAR(15)     NOT NULL,
    mobile_verified_at      TIMESTAMP       NOT NULL,
    description             TEXT,
    address_line            VARCHAR(255)    NOT NULL,
    city                    VARCHAR(80)     NOT NULL,
    state                   VARCHAR(80)     NOT NULL,
    pincode                 VARCHAR(6)       NOT NULL,
    latitude                NUMERIC(10, 7),
    longitude               NUMERIC(10, 7),
    map_url                 VARCHAR(512),
    monthly_price           NUMERIC(12, 2)  NOT NULL,
    meal_price              NUMERIC(12, 2)  NOT NULL,
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
    CONSTRAINT pk_mess_registrations PRIMARY KEY (id),
    CONSTRAINT uq_mess_registrations_reference UNIQUE (reference),
    CONSTRAINT fk_mess_registrations_converted_space
        FOREIGN KEY (converted_space_id) REFERENCES spaces (id),
    CONSTRAINT fk_mess_registrations_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_mess_registrations_status
        CHECK (status IN ('PENDING', 'IN_REVIEW', 'CONTACTED', 'CONVERTED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT chk_mess_registrations_source
        CHECK (source IN ('PUBLIC_WEBSITE')),
    CONSTRAINT chk_mess_registrations_pincode
        CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT chk_mess_registrations_monthly_price
        CHECK (monthly_price >= 0),
    CONSTRAINT chk_mess_registrations_meal_price
        CHECK (meal_price >= 0),
    CONSTRAINT chk_mess_registrations_capacity
        CHECK (capacity_estimate IS NULL OR capacity_estimate >= 0)
);

CREATE INDEX idx_mess_registrations_status_created
    ON mess_registrations (status, created_at DESC);

CREATE INDEX idx_mess_registrations_mobile
    ON mess_registrations (mobile_number);

CREATE INDEX idx_mess_registrations_duplicate_probe
    ON mess_registrations (mobile_number, pincode, created_at DESC);
