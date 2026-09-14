-- Indexes for authenticated space discovery (active spaces filtered by type / name).

CREATE INDEX IF NOT EXISTS idx_spaces_active_type_created
    ON spaces (is_active, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_spaces_active_name_lower
    ON spaces (is_active, lower(name));
