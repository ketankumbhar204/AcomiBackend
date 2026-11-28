-- Admin listing marketing status for property/mess registrations and native spaces.
-- Registration columns survive conversion via converted_space_id joins.
-- Space columns cover active spaces that never had a registration lead.

ALTER TABLE property_registrations
    ADD COLUMN IF NOT EXISTS marketing_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SHARED',
    ADD COLUMN IF NOT EXISTS whatsapp_availability VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS last_shared_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_shared_by UUID,
    ADD COLUMN IF NOT EXISTS marketing_notes TEXT;

ALTER TABLE mess_registrations
    ADD COLUMN IF NOT EXISTS marketing_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SHARED',
    ADD COLUMN IF NOT EXISTS whatsapp_availability VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS last_shared_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_shared_by UUID,
    ADD COLUMN IF NOT EXISTS marketing_notes TEXT;

ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS marketing_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SHARED',
    ADD COLUMN IF NOT EXISTS whatsapp_availability VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS last_shared_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_shared_by UUID,
    ADD COLUMN IF NOT EXISTS marketing_notes TEXT;

ALTER TABLE property_registrations
    DROP CONSTRAINT IF EXISTS chk_property_registrations_marketing_status;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_marketing_status
    CHECK (marketing_status IN ('NOT_SHARED', 'SHARED'));

ALTER TABLE property_registrations
    DROP CONSTRAINT IF EXISTS chk_property_registrations_whatsapp_availability;
ALTER TABLE property_registrations
    ADD CONSTRAINT chk_property_registrations_whatsapp_availability
    CHECK (whatsapp_availability IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE'));

ALTER TABLE mess_registrations
    DROP CONSTRAINT IF EXISTS chk_mess_registrations_marketing_status;
ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_marketing_status
    CHECK (marketing_status IN ('NOT_SHARED', 'SHARED'));

ALTER TABLE mess_registrations
    DROP CONSTRAINT IF EXISTS chk_mess_registrations_whatsapp_availability;
ALTER TABLE mess_registrations
    ADD CONSTRAINT chk_mess_registrations_whatsapp_availability
    CHECK (whatsapp_availability IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE'));

ALTER TABLE spaces
    DROP CONSTRAINT IF EXISTS chk_spaces_marketing_status;
ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_marketing_status
    CHECK (marketing_status IN ('NOT_SHARED', 'SHARED'));

ALTER TABLE spaces
    DROP CONSTRAINT IF EXISTS chk_spaces_whatsapp_availability;
ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_whatsapp_availability
    CHECK (whatsapp_availability IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE'));

CREATE INDEX IF NOT EXISTS idx_property_registrations_marketing
    ON property_registrations (marketing_status, whatsapp_availability);

CREATE INDEX IF NOT EXISTS idx_mess_registrations_marketing
    ON mess_registrations (marketing_status, whatsapp_availability);

CREATE INDEX IF NOT EXISTS idx_spaces_marketing
    ON spaces (marketing_status, whatsapp_availability)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS admin_listing_marketing_activities (
    id                      UUID PRIMARY KEY,
    listing_kind            VARCHAR(20)  NOT NULL,
    property_registration_id UUID REFERENCES property_registrations (id),
    mess_registration_id    UUID REFERENCES mess_registrations (id),
    space_id                UUID REFERENCES spaces (id),
    channel                 VARCHAR(20)  NOT NULL,
    outcome                 VARCHAR(40)  NOT NULL,
    template_key            VARCHAR(80),
    note                    TEXT,
    message_snapshot        TEXT,
    actor_user_id           UUID         NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    CONSTRAINT chk_admin_listing_marketing_kind CHECK (
        listing_kind IN ('PROPERTY', 'MESS')
    ),
    CONSTRAINT chk_admin_listing_marketing_channel CHECK (
        channel IN ('WHATSAPP', 'CALL')
    ),
    CONSTRAINT chk_admin_listing_marketing_outcome CHECK (
        outcome IN (
            'WHATSAPP_OPENED',
            'SHARED_CONFIRMED',
            'WHATSAPP_UNAVAILABLE',
            'CALL_ATTEMPTED',
            'CONTACTED_BY_PHONE',
            'SKIPPED'
        )
    ),
    CONSTRAINT chk_admin_listing_marketing_target CHECK (
        property_registration_id IS NOT NULL
        OR mess_registration_id IS NOT NULL
        OR space_id IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_admin_listing_marketing_activities_property
    ON admin_listing_marketing_activities (property_registration_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_listing_marketing_activities_mess
    ON admin_listing_marketing_activities (mess_registration_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_listing_marketing_activities_space
    ON admin_listing_marketing_activities (space_id, created_at DESC);
