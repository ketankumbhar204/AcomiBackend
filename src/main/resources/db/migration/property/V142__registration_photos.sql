-- Photos submitted with a public property or mess registration lead.
-- Bytes live on the lead so a failed submission rolls back with the lead
-- and does not leave an unowned stored_files row (that table requires an uploader).

CREATE TABLE registration_photos (
    id                       UUID         NOT NULL,
    property_registration_id UUID,
    mess_registration_id     UUID,
    display_order            INT          NOT NULL,
    content_type             VARCHAR(40)  NOT NULL,
    original_filename        VARCHAR(255),
    byte_size                INT          NOT NULL,
    content                  BYTEA        NOT NULL,
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_registration_photos PRIMARY KEY (id),
    CONSTRAINT fk_registration_photos_property
        FOREIGN KEY (property_registration_id) REFERENCES property_registrations (id),
    CONSTRAINT fk_registration_photos_mess
        FOREIGN KEY (mess_registration_id) REFERENCES mess_registrations (id),
    CONSTRAINT chk_registration_photos_one_parent CHECK (
        (property_registration_id IS NOT NULL AND mess_registration_id IS NULL)
        OR (property_registration_id IS NULL AND mess_registration_id IS NOT NULL)
    ),
    CONSTRAINT chk_registration_photos_byte_size CHECK (byte_size > 0 AND byte_size <= 3145728),
    CONSTRAINT chk_registration_photos_content_type CHECK (
        content_type IN ('image/jpeg', 'image/png', 'image/webp')
    )
);

CREATE INDEX idx_registration_photos_property
    ON registration_photos (property_registration_id, display_order);

CREATE INDEX idx_registration_photos_mess
    ON registration_photos (mess_registration_id, display_order);
