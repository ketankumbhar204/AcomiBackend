-- Account-owned service-provider directory.
-- A contact belongs to one user account and may be linked to many of that account's spaces.
-- This is not a membership, invitation, or space role.

CREATE TABLE service_contacts (
    id                    UUID          NOT NULL,
    owner_user_id         UUID          NOT NULL,
    business_name         VARCHAR(150)  NOT NULL,
    category              VARCHAR(40)   NOT NULL,
    custom_category_name  VARCHAR(80),
    contact_person_name   VARCHAR(150),
    primary_phone         VARCHAR(15),
    alternate_phone       VARCHAR(15),
    whatsapp_phone        VARCHAR(15),
    email                 VARCHAR(254),
    description           VARCHAR(500),
    address               VARCHAR(300),
    notes                 VARCHAR(2000),
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_by_user_id    UUID,
    updated_by_user_id    UUID,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_service_contacts PRIMARY KEY (id),
    CONSTRAINT fk_service_contacts_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_service_contacts_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_service_contacts_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_service_contacts_name CHECK (char_length(btrim(business_name)) > 0),
    CONSTRAINT chk_service_contacts_category CHECK (category IN (
        'GROCERIES_WHOLESALE',
        'MILK_DAIRY',
        'WATER_SUPPLY',
        'FOOD_BAKERY',
        'VEGETABLES_FRUITS',
        'OTHER_FOOD',
        'ELECTRICAL',
        'PLUMBING',
        'CARPENTRY',
        'CIVIL_PAINTING',
        'APPLIANCES_HVAC',
        'CLEANING',
        'PEST_WASTE',
        'CARE_MEDICAL',
        'GAS_KITCHEN',
        'INTERNET',
        'SECURITY',
        'FIRE_SAFETY',
        'LIFT_FACILITY',
        'TRANSPORT',
        'PROFESSIONAL',
        'OTHER'
    )),
    CONSTRAINT chk_service_contacts_custom_category CHECK (
        (category = 'OTHER'
            AND custom_category_name IS NOT NULL
            AND char_length(btrim(custom_category_name)) BETWEEN 2 AND 80)
        OR (category <> 'OTHER' AND custom_category_name IS NULL)
    ),
    CONSTRAINT chk_service_contacts_method CHECK (
        primary_phone IS NOT NULL
        OR alternate_phone IS NOT NULL
        OR whatsapp_phone IS NOT NULL
        OR email IS NOT NULL
    ),
    CONSTRAINT chk_service_contacts_primary_phone CHECK (
        primary_phone IS NULL OR primary_phone ~ '^[6-9][0-9]{9}$'
    ),
    CONSTRAINT chk_service_contacts_alternate_phone CHECK (
        alternate_phone IS NULL OR alternate_phone ~ '^[6-9][0-9]{9}$'
    ),
    CONSTRAINT chk_service_contacts_whatsapp_phone CHECK (
        whatsapp_phone IS NULL OR whatsapp_phone ~ '^[6-9][0-9]{9}$'
    )
);

CREATE INDEX idx_service_contacts_owner_updated
    ON service_contacts (owner_user_id, is_active, updated_at DESC);

CREATE INDEX idx_service_contacts_owner_category
    ON service_contacts (owner_user_id, category);

CREATE INDEX idx_service_contacts_owner_name
    ON service_contacts (owner_user_id, business_name);

CREATE INDEX idx_service_contacts_owner_primary_phone
    ON service_contacts (owner_user_id, primary_phone);

CREATE INDEX idx_service_contacts_owner_whatsapp
    ON service_contacts (owner_user_id, whatsapp_phone);

CREATE TABLE service_contact_spaces (
    id                  UUID      NOT NULL,
    service_contact_id  UUID      NOT NULL,
    space_id            UUID      NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_service_contact_spaces PRIMARY KEY (id),
    CONSTRAINT fk_service_contact_spaces_contact
        FOREIGN KEY (service_contact_id) REFERENCES service_contacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_service_contact_spaces_space
        FOREIGN KEY (space_id) REFERENCES spaces (id) ON DELETE CASCADE,
    CONSTRAINT uq_service_contact_spaces UNIQUE (service_contact_id, space_id)
);

CREATE INDEX idx_service_contact_spaces_space
    ON service_contact_spaces (space_id);

CREATE INDEX idx_service_contact_spaces_contact
    ON service_contact_spaces (service_contact_id);
