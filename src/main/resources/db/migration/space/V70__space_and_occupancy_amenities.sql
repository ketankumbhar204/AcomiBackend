CREATE TABLE space_amenities (
    id              UUID            NOT NULL,
    space_id        UUID            NOT NULL,
    amenity_code    VARCHAR(50)     NOT NULL,
    custom_label    VARCHAR(120),
    display_order   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_space_amenities PRIMARY KEY (id),
    CONSTRAINT fk_space_amenities_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT chk_space_amenities_custom_label
        CHECK (amenity_code <> 'CUSTOM' OR (custom_label IS NOT NULL AND LENGTH(TRIM(custom_label)) > 0))
);

CREATE INDEX idx_space_amenities_space_id ON space_amenities (space_id);

CREATE TABLE occupancy_amenities (
    id              UUID            NOT NULL,
    occupancy_id    UUID            NOT NULL,
    amenity_code    VARCHAR(50)     NOT NULL,
    custom_label    VARCHAR(120),
    display_order   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_occupancy_amenities PRIMARY KEY (id),
    CONSTRAINT fk_occupancy_amenities_occupancy
        FOREIGN KEY (occupancy_id) REFERENCES occupancies (id) ON DELETE CASCADE,
    CONSTRAINT chk_occupancy_amenities_custom_label
        CHECK (amenity_code <> 'CUSTOM' OR (custom_label IS NOT NULL AND LENGTH(TRIM(custom_label)) > 0))
);

CREATE INDEX idx_occupancy_amenities_occupancy_id ON occupancy_amenities (occupancy_id);
