-- Channel-specific contact-detail deliveries for space enquiries.
-- Enquiry lifecycle (PENDING/SHARED/...) stays on space_enquiries.
-- APP vs EMAIL deliveries are tracked independently so cross-channel
-- delivery is allowed while same-channel active delivery is blocked.

CREATE TABLE space_enquiry_deliveries (
    id                  UUID PRIMARY KEY,
    enquiry_id          UUID         NOT NULL REFERENCES space_enquiries (id),
    space_id            UUID         NOT NULL,
    requester_user_id   UUID         NOT NULL,
    delivery_channel    VARCHAR(20)  NOT NULL,
    recipient_email     VARCHAR(255),
    delivered_at        TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT chk_space_enquiry_deliveries_channel CHECK (
        delivery_channel IN ('APP', 'EMAIL')
    ),
    CONSTRAINT chk_space_enquiry_deliveries_recipient CHECK (
        (delivery_channel = 'APP' AND recipient_email IS NULL)
        OR (delivery_channel = 'EMAIL' AND recipient_email IS NOT NULL)
    )
);

-- One APP delivery row per (user, listing). Re-delivery after expiry updates the row.
CREATE UNIQUE INDEX uq_space_enquiry_deliveries_app
    ON space_enquiry_deliveries (space_id, requester_user_id)
    WHERE delivery_channel = 'APP';

-- One EMAIL delivery row per (user, listing, normalized destination email).
CREATE UNIQUE INDEX uq_space_enquiry_deliveries_email
    ON space_enquiry_deliveries (space_id, requester_user_id, recipient_email)
    WHERE delivery_channel = 'EMAIL';

CREATE INDEX idx_space_enquiry_deliveries_enquiry
    ON space_enquiry_deliveries (enquiry_id);

CREATE INDEX idx_space_enquiry_deliveries_requester_space
    ON space_enquiry_deliveries (requester_user_id, space_id, delivery_channel);

-- Backfill APP deliveries from ANDROID SHARED enquiries (in-app contact).
-- DISTINCT ON guards against any historical duplicate SHARED rows.
INSERT INTO space_enquiry_deliveries (
    id,
    enquiry_id,
    space_id,
    requester_user_id,
    delivery_channel,
    recipient_email,
    delivered_at,
    expires_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    s.id,
    s.space_id,
    s.requester_user_id,
    'APP',
    NULL,
    s.delivered_at,
    s.expires_at,
    s.created_at,
    s.updated_at
FROM (
    SELECT DISTINCT ON (e.space_id, e.requester_user_id)
        e.id,
        e.space_id,
        e.requester_user_id,
        COALESCE(e.shared_at, e.requested_at) AS delivered_at,
        e.expires_at,
        COALESCE(e.shared_at, e.requested_at, e.created_at) AS created_at,
        COALESCE(e.updated_at, e.created_at) AS updated_at
    FROM space_enquiries e
    WHERE e.client_channel = 'ANDROID'
      AND e.status = 'SHARED'
    ORDER BY e.space_id, e.requester_user_id, COALESCE(e.shared_at, e.requested_at) DESC NULLS LAST
) s
WHERE NOT EXISTS (
    SELECT 1
    FROM space_enquiry_deliveries d
    WHERE d.space_id = s.space_id
      AND d.requester_user_id = s.requester_user_id
      AND d.delivery_channel = 'APP'
);

-- Backfill EMAIL deliveries from contact_email_sent_at.
-- recipient_email comes from space_enquiries.requester_email, which deliverContactByEmail
-- updates to the destination address before setting contact_email_sent_at.
-- DISTINCT ON keeps the latest send when multiple historical enquiries share the same
-- (user, listing, normalized email) — older destinations beyond the last requester_email
-- are not inventable from this table alone.
INSERT INTO space_enquiry_deliveries (
    id,
    enquiry_id,
    space_id,
    requester_user_id,
    delivery_channel,
    recipient_email,
    delivered_at,
    expires_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    s.id,
    s.space_id,
    s.requester_user_id,
    'EMAIL',
    s.recipient_email,
    s.delivered_at,
    s.expires_at,
    s.created_at,
    s.updated_at
FROM (
    SELECT DISTINCT ON (e.space_id, e.requester_user_id, lower(trim(e.requester_email)))
        e.id,
        e.space_id,
        e.requester_user_id,
        lower(trim(e.requester_email)) AS recipient_email,
        e.contact_email_sent_at AS delivered_at,
        e.expires_at,
        e.contact_email_sent_at AS created_at,
        COALESCE(e.updated_at, e.contact_email_sent_at) AS updated_at
    FROM space_enquiries e
    WHERE e.contact_email_sent_at IS NOT NULL
      AND e.requester_email IS NOT NULL
      AND trim(e.requester_email) <> ''
    ORDER BY
        e.space_id,
        e.requester_user_id,
        lower(trim(e.requester_email)),
        e.contact_email_sent_at DESC NULLS LAST
) s
WHERE NOT EXISTS (
    SELECT 1
    FROM space_enquiry_deliveries d
    WHERE d.space_id = s.space_id
      AND d.requester_user_id = s.requester_user_id
      AND d.delivery_channel = 'EMAIL'
      AND d.recipient_email = s.recipient_email
);
