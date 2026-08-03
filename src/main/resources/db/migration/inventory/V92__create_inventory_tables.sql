-- Space-scoped operational inventory (food / assets / furniture).
-- Seeded once per space by InventorySeedService (space create + lazy GET).

CREATE TABLE inventory_categories (
    id              UUID PRIMARY KEY,
    space_id        UUID         NOT NULL REFERENCES spaces (id),
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    icon_key        VARCHAR(40)  NOT NULL DEFAULT 'Package',
    sort_order      INT          NOT NULL DEFAULT 0,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_categories_space_code UNIQUE (space_id, code)
);

CREATE INDEX idx_inventory_categories_space
    ON inventory_categories (space_id, sort_order);

CREATE TABLE inventory_suppliers (
    id              UUID PRIMARY KEY,
    space_id        UUID         NOT NULL REFERENCES spaces (id),
    name            VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    address         TEXT,
    notes           TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_suppliers_space
    ON inventory_suppliers (space_id, name);

CREATE TABLE inventory_items (
    id                  UUID PRIMARY KEY,
    space_id            UUID           NOT NULL REFERENCES spaces (id),
    category_id         UUID           NOT NULL REFERENCES inventory_categories (id),
    name                VARCHAR(150)   NOT NULL,
    unit                VARCHAR(20)    NOT NULL,
    minimum_stock       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    current_stock       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    reserved_stock      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    purchase_price      NUMERIC(14, 2),
    average_price       NUMERIC(14, 2),
    supplier_id         UUID           REFERENCES inventory_suppliers (id),
    location            VARCHAR(150),
    barcode             VARCHAR(80),
    notes               TEXT,
    status_override     VARCHAR(30),
    expires_at          TIMESTAMP,
    warranty_until      TIMESTAMP,
    assigned_entity_type VARCHAR(20),
    assigned_entity_id  UUID,
    is_default          BOOLEAN        NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_items_unit CHECK (
        unit IN ('KG', 'LITRE', 'PIECE', 'PACKET', 'DOZEN', 'METRE', 'SET', 'OTHER')
    ),
    CONSTRAINT chk_inventory_items_stock CHECK (
        current_stock >= 0 AND reserved_stock >= 0 AND minimum_stock >= 0
    )
);

CREATE INDEX idx_inventory_items_space
    ON inventory_items (space_id, is_active);
CREATE INDEX idx_inventory_items_category
    ON inventory_items (category_id);
CREATE INDEX idx_inventory_items_supplier
    ON inventory_items (supplier_id);

CREATE TABLE inventory_transactions (
    id              UUID PRIMARY KEY,
    space_id        UUID           NOT NULL REFERENCES spaces (id),
    item_id         UUID           NOT NULL REFERENCES inventory_items (id),
    item_name       VARCHAR(150)   NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    unit            VARCHAR(20)    NOT NULL,
    reason          VARCHAR(255),
    reference       VARCHAR(100),
    supplier_id     UUID           REFERENCES inventory_suppliers (id),
    supplier_name   VARCHAR(150),
    amount          NUMERIC(14, 2),
    actor_name      VARCHAR(100),
    actor_user_id   UUID,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_transactions_type CHECK (
        type IN ('STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT', 'TRANSFER', 'PURCHASE', 'CONSUMPTION')
    ),
    CONSTRAINT chk_inventory_transactions_qty CHECK (quantity >= 0)
);

CREATE INDEX idx_inventory_transactions_space_created
    ON inventory_transactions (space_id, created_at DESC);
CREATE INDEX idx_inventory_transactions_item
    ON inventory_transactions (item_id, created_at DESC);
