-- Canonical file metadata. Object bytes live in the configured storage provider.
-- Legacy URL/base64 columns are retained so existing rows stay readable.

CREATE TABLE stored_files (
    id                      UUID         NOT NULL,
    purpose                 VARCHAR(40)  NOT NULL,
    visibility              VARCHAR(20)  NOT NULL,
    status                  VARCHAR(30)  NOT NULL,
    original_filename       VARCHAR(255),
    content_type            VARCHAR(100) NOT NULL,
    byte_size               BIGINT,
    checksum_sha256         VARCHAR(64),
    checksum_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    storage_provider        VARCHAR(32)  NOT NULL,
    bucket                  VARCHAR(128) NOT NULL,
    object_key              VARCHAR(512) NOT NULL,
    uploaded_by_user_id     UUID         NOT NULL,
    space_id                UUID,
    associated              BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at              TIMESTAMP,
    deleted_at              TIMESTAMP,
    purge_after             TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_stored_files PRIMARY KEY (id),
    CONSTRAINT fk_stored_files_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_stored_files_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT chk_stored_files_purpose CHECK (purpose IN (
        'PROFILE_PHOTO',
        'IDENTITY_DOCUMENT',
        'ADDRESS_PROOF',
        'MEMBER_DOCUMENT',
        'PAYMENT_PROOF',
        'MEAL_PAYMENT_PROOF',
        'SUBSCRIPTION_PAYMENT_PROOF',
        'COMPLAINT_ATTACHMENT'
    )),
    CONSTRAINT chk_stored_files_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT chk_stored_files_status CHECK (status IN (
        'PENDING',
        'ACTIVE',
        'PENDING_DELETE',
        'DELETED',
        'FAILED'
    ))
);

CREATE UNIQUE INDEX uq_stored_files_provider_bucket_key
    ON stored_files (storage_provider, bucket, object_key)
    WHERE status <> 'DELETED';

CREATE INDEX idx_stored_files_status_expires
    ON stored_files (status, expires_at);

CREATE INDEX idx_stored_files_status_purge
    ON stored_files (status, purge_after);

CREATE INDEX idx_stored_files_space_purpose
    ON stored_files (space_id, purpose);

CREATE INDEX idx_stored_files_uploader
    ON stored_files (uploaded_by_user_id);

CREATE INDEX idx_stored_files_status_associated_created
    ON stored_files (status, associated, created_at);

ALTER TABLE users
    ADD COLUMN profile_photo_file_id UUID;

ALTER TABLE users
    ADD CONSTRAINT fk_users_profile_photo_file
    FOREIGN KEY (profile_photo_file_id) REFERENCES stored_files (id);

CREATE INDEX idx_users_profile_photo_file_id ON users (profile_photo_file_id);

ALTER TABLE member_documents
    ADD COLUMN file_id UUID;

ALTER TABLE member_documents
    ADD CONSTRAINT fk_member_documents_file
    FOREIGN KEY (file_id) REFERENCES stored_files (id);

CREATE INDEX idx_member_documents_file_id ON member_documents (file_id);

ALTER TABLE space_payments
    ADD COLUMN proof_file_id UUID;

ALTER TABLE space_payments
    ADD CONSTRAINT fk_space_payments_proof_file
    FOREIGN KEY (proof_file_id) REFERENCES stored_files (id);

CREATE INDEX idx_space_payments_proof_file_id ON space_payments (proof_file_id);

ALTER TABLE meal_poll_day_payments
    ADD COLUMN proof_file_id UUID;

ALTER TABLE meal_poll_day_payments
    ADD CONSTRAINT fk_meal_poll_day_payments_proof_file
    FOREIGN KEY (proof_file_id) REFERENCES stored_files (id);

CREATE INDEX idx_meal_poll_day_payments_proof_file_id
    ON meal_poll_day_payments (proof_file_id);

ALTER TABLE subscription_activation_requests
    ADD COLUMN payment_proof_file_id UUID;

ALTER TABLE subscription_activation_requests
    ADD CONSTRAINT fk_subscription_activation_proof_file
    FOREIGN KEY (payment_proof_file_id) REFERENCES stored_files (id);

CREATE INDEX idx_subscription_activation_proof_file_id
    ON subscription_activation_requests (payment_proof_file_id);

ALTER TABLE space_complaint_attachments
    ADD COLUMN file_id UUID;

ALTER TABLE space_complaint_attachments
    ADD CONSTRAINT fk_space_complaint_attachments_file
    FOREIGN KEY (file_id) REFERENCES stored_files (id);

CREATE INDEX idx_space_complaint_attachments_file_id
    ON space_complaint_attachments (file_id);
