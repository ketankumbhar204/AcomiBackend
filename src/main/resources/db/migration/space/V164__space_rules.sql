CREATE TABLE space_rules (
    id              UUID            NOT NULL,
    space_id        UUID            NOT NULL,
    rule_code       VARCHAR(50)     NOT NULL,
    rule_text       VARCHAR(500)    NOT NULL,
    display_order   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_space_rules PRIMARY KEY (id),
    CONSTRAINT fk_space_rules_space
        FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT chk_space_rules_rule_text
        CHECK (LENGTH(TRIM(rule_text)) > 0)
);

CREATE INDEX idx_space_rules_space_id ON space_rules (space_id);
