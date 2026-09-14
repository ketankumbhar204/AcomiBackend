-- Contact-enquiry audit history. Owner contact is NOT stored here; Admin
-- resolves it at review/share time from the live Space / registration source.

CREATE TABLE space_enquiries (
    id                      UUID PRIMARY KEY,
    space_id                UUID         NOT NULL REFERENCES spaces (id),
    space_name_snapshot     VARCHAR(150) NOT NULL,
    requester_user_id       UUID         NOT NULL REFERENCES users (id),
    requester_name_snapshot VARCHAR(120) NOT NULL,
    requester_email         VARCHAR(255) NOT NULL,
    requester_type          VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    requested_at            TIMESTAMP    NOT NULL,
    expires_at              TIMESTAMP    NOT NULL,
    reviewed_at             TIMESTAMP,
    shared_at               TIMESTAMP,
    shared_by_admin_id      UUID REFERENCES users (id),
    rejected_at             TIMESTAMP,
    rejection_reason        VARCHAR(500),
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL,
    CONSTRAINT chk_space_enquiries_status CHECK (
        status IN ('PENDING', 'SHARED', 'REJECTED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT chk_space_enquiries_requester_type CHECK (
        requester_type IN ('MEMBER', 'OWNER')
    )
);

CREATE UNIQUE INDEX uq_space_enquiries_pending_requester_space
    ON space_enquiries (space_id, requester_user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_space_enquiries_requester_requested
    ON space_enquiries (requester_user_id, requested_at DESC);

CREATE INDEX idx_space_enquiries_status_requested
    ON space_enquiries (status, requested_at DESC);

CREATE INDEX idx_space_enquiries_pending_expires
    ON space_enquiries (expires_at)
    WHERE status = 'PENDING';
