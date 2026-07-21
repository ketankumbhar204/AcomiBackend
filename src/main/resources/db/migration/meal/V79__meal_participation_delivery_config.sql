-- Phase 1: Owner delivery configuration per meal participation × meal type.
-- No backfill — existing default_delivery_location_id and last_delivery remain untouched.

CREATE TABLE meal_participation_delivery_allowed (
    id                      UUID         NOT NULL,
    participation_id        UUID         NOT NULL,
    meal_type               VARCHAR(20)  NOT NULL,
    delivery_location_id    UUID         NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_meal_participation_delivery_allowed PRIMARY KEY (id),
    CONSTRAINT fk_meal_part_delivery_allowed_participation
        FOREIGN KEY (participation_id) REFERENCES meal_participations (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_part_delivery_allowed_location
        FOREIGN KEY (delivery_location_id) REFERENCES meal_delivery_locations (id),
    CONSTRAINT uq_meal_part_delivery_allowed_part_meal_loc
        UNIQUE (participation_id, meal_type, delivery_location_id)
);

CREATE INDEX idx_meal_part_delivery_allowed_participation
    ON meal_participation_delivery_allowed (participation_id);

CREATE INDEX idx_meal_part_delivery_allowed_part_meal
    ON meal_participation_delivery_allowed (participation_id, meal_type);

CREATE TABLE meal_participation_delivery_default (
    id                      UUID         NOT NULL,
    participation_id        UUID         NOT NULL,
    meal_type               VARCHAR(20)  NOT NULL,
    delivery_location_id    UUID         NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_meal_participation_delivery_default PRIMARY KEY (id),
    CONSTRAINT fk_meal_part_delivery_default_participation
        FOREIGN KEY (participation_id) REFERENCES meal_participations (id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_part_delivery_default_location
        FOREIGN KEY (delivery_location_id) REFERENCES meal_delivery_locations (id),
    CONSTRAINT uq_meal_part_delivery_default_part_meal
        UNIQUE (participation_id, meal_type)
);

CREATE INDEX idx_meal_part_delivery_default_participation
    ON meal_participation_delivery_default (participation_id);
