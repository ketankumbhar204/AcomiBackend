-- Complaints MVP: tickets, comments, attachments, timeline

CREATE TABLE space_complaints (
    id                          UUID PRIMARY KEY,
    space_id                    UUID         NOT NULL REFERENCES spaces (id),
    created_by_member_id        UUID         NOT NULL REFERENCES members (id),
    created_by_user_id          UUID         NOT NULL,
    category                    VARCHAR(30)  NOT NULL,
    priority                    VARCHAR(20)  NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT         NOT NULL,
    assigned_to_membership_id   UUID         REFERENCES space_memberships (id),
    resolution_summary          TEXT,
    resolved_at                 TIMESTAMP,
    resolved_by_user_id         UUID,
    reopened_at                 TIMESTAMP,
    closed_at                   TIMESTAMP,
    cancelled_at                TIMESTAMP,
    meal_date                   DATE,
    meal_type                   VARCHAR(20),
    created_at                  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_space_complaints_category CHECK (
        category IN (
            'MAINTENANCE', 'HOUSEKEEPING', 'FOOD', 'FOOD_QUALITY', 'FOOD_SERVICE',
            'BILLING', 'SAFETY', 'SERVICE', 'OTHER'
        )
    ),
    CONSTRAINT chk_space_complaints_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')
    ),
    CONSTRAINT chk_space_complaints_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')
    ),
    CONSTRAINT chk_space_complaints_meal_type CHECK (
        meal_type IS NULL OR meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')
    )
);

CREATE INDEX idx_space_complaints_space_status ON space_complaints (space_id, status);
CREATE INDEX idx_space_complaints_space_created ON space_complaints (space_id, created_at DESC);
CREATE INDEX idx_space_complaints_created_by_member ON space_complaints (created_by_member_id);
CREATE INDEX idx_space_complaints_assignee ON space_complaints (assigned_to_membership_id);
CREATE INDEX idx_space_complaints_category ON space_complaints (space_id, category);
CREATE INDEX idx_space_complaints_priority ON space_complaints (space_id, priority);

CREATE TABLE space_complaint_comments (
    id                  UUID PRIMARY KEY,
    complaint_id        UUID         NOT NULL REFERENCES space_complaints (id) ON DELETE CASCADE,
    author_member_id    UUID         REFERENCES members (id),
    author_user_id      UUID         NOT NULL,
    body                TEXT         NOT NULL,
    is_internal         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_space_complaint_comments_complaint ON space_complaint_comments (complaint_id, created_at);

CREATE TABLE space_complaint_attachments (
    id                  UUID PRIMARY KEY,
    complaint_id        UUID         NOT NULL REFERENCES space_complaints (id) ON DELETE CASCADE,
    storage_url         TEXT         NOT NULL,
    content_type        VARCHAR(100),
    file_name           VARCHAR(255),
    created_by_user_id  UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_space_complaint_attachments_complaint ON space_complaint_attachments (complaint_id);

CREATE TABLE space_complaint_timeline_events (
    id              UUID PRIMARY KEY,
    complaint_id    UUID         NOT NULL REFERENCES space_complaints (id) ON DELETE CASCADE,
    event_type      VARCHAR(30)  NOT NULL,
    performed_at    TIMESTAMP    NOT NULL,
    remarks         TEXT,
    performed_by    UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_space_complaint_timeline_event_type CHECK (
        event_type IN (
            'CREATED', 'STATUS_CHANGED', 'COMMENTED', 'INTERNAL_NOTE',
            'ATTACHMENT_ADDED', 'ASSIGNED', 'PRIORITY_CHANGED',
            'REOPENED', 'RESOLVED', 'CLOSED', 'CANCELLED'
        )
    )
);

CREATE INDEX idx_space_complaint_timeline_complaint ON space_complaint_timeline_events (complaint_id, performed_at);
