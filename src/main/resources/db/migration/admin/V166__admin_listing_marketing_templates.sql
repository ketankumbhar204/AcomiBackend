-- Custom promotional WhatsApp message templates for admin listing marketing.
-- Built-in templates remain client-side; this table stores admin-created templates only.

CREATE TABLE IF NOT EXISTS admin_listing_marketing_templates (
    id              UUID PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(255),
    body            TEXT         NOT NULL,
    applicability   VARCHAR(20)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INT          NOT NULL DEFAULT 0,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE admin_listing_marketing_templates
    DROP CONSTRAINT IF EXISTS chk_admin_listing_marketing_templates_applicability;
ALTER TABLE admin_listing_marketing_templates
    ADD CONSTRAINT chk_admin_listing_marketing_templates_applicability
    CHECK (applicability IN ('PROPERTY', 'MESS', 'BOTH'));

CREATE INDEX IF NOT EXISTS idx_admin_listing_marketing_templates_active_order
    ON admin_listing_marketing_templates (active, display_order, name);
