-- Outbound email audit / send log.
-- Stores delivery metadata only. Do not store email bodies (they may contain
-- owner contact details shared through the authorised enquiry-share channel).
-- Idempotency: one row per idempotency_key (e.g. ENQUIRY_SHARED:{enquiryId}).

CREATE TABLE IF NOT EXISTS email_send_logs (
    id                    UUID PRIMARY KEY,
    event_type            VARCHAR(40)  NOT NULL,
    recipient_user_id     UUID,
    recipient_email       VARCHAR(255) NOT NULL,
    from_email            VARCHAR(255) NOT NULL,
    reply_to              VARCHAR(255),
    subject               VARCHAR(200) NOT NULL,
    related_entity_type   VARCHAR(40),
    related_entity_id     UUID,
    idempotency_key       VARCHAR(120) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    attempt_count         INTEGER      NOT NULL DEFAULT 0,
    provider_message_id   VARCHAR(200),
    sent_at               TIMESTAMP WITHOUT TIME ZONE,
    failed_at             TIMESTAMP WITHOUT TIME ZONE,
    last_error            VARCHAR(500),
    last_attempt_at       TIMESTAMP WITHOUT TIME ZONE,
    created_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT uq_email_send_logs_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_email_send_logs_status CHECK (
        status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')
    )
);

CREATE INDEX IF NOT EXISTS idx_email_send_logs_status_attempt
    ON email_send_logs (status, last_attempt_at);

CREATE INDEX IF NOT EXISTS idx_email_send_logs_related_entity
    ON email_send_logs (related_entity_type, related_entity_id);

CREATE INDEX IF NOT EXISTS idx_email_send_logs_event_created
    ON email_send_logs (event_type, created_at DESC);
