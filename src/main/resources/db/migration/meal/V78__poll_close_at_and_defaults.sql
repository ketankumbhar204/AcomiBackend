-- Poll auto-close: per-poll datetime + space defaults + timezone.
ALTER TABLE spaces
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS poll_close_breakfast_day_offset VARCHAR(20) NOT NULL DEFAULT 'PREVIOUS_DAY',
    ADD COLUMN IF NOT EXISTS poll_close_breakfast_time TIME NOT NULL DEFAULT '20:00:00',
    ADD COLUMN IF NOT EXISTS poll_close_lunch_day_offset VARCHAR(20) NOT NULL DEFAULT 'SAME_DAY',
    ADD COLUMN IF NOT EXISTS poll_close_lunch_time TIME NOT NULL DEFAULT '08:00:00',
    ADD COLUMN IF NOT EXISTS poll_close_dinner_day_offset VARCHAR(20) NOT NULL DEFAULT 'SAME_DAY',
    ADD COLUMN IF NOT EXISTS poll_close_dinner_time TIME NOT NULL DEFAULT '13:00:00';

ALTER TABLE meal_polls
    ADD COLUMN IF NOT EXISTS poll_close_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS close_source VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_meal_polls_open_close_at
    ON meal_polls (status, poll_close_at)
    WHERE status = 'OPEN' AND poll_close_at IS NOT NULL;
