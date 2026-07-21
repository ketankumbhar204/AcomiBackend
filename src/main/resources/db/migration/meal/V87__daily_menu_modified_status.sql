-- Customer-visible menus stay on the last shared snapshot while owners edit (MODIFIED).
ALTER TABLE daily_menus DROP CONSTRAINT IF EXISTS chk_daily_menus_status;
ALTER TABLE daily_menus
    ADD CONSTRAINT chk_daily_menus_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'MODIFIED'));

ALTER TABLE daily_menus
    ADD COLUMN IF NOT EXISTS published_snapshot TEXT;
