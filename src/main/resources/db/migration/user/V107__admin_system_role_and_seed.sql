-- Platform admin role on users. One seeded admin for local/staging; change password after first login.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS system_role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_system_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_system_role CHECK (system_role IN ('USER', 'ADMIN'));

-- Seeded admin: mobile 9000000001, password Admin@12345 (bcrypt via Spring delegating encoder).
INSERT INTO users (
    id,
    mobile_number,
    full_name,
    is_active,
    password_hash,
    system_role,
    mobile_verified_at,
    created_at,
    updated_at
)
SELECT
    'a0000000-0000-4000-8000-000000000001'::uuid,
    '9000000001',
    'ACOMI Admin',
    TRUE,
    '{bcrypt}$2a$10$Al61Tg31kqQDaRQvPuuEhuEWKPRh4VPOwVc/M0Kpde/IEIEJ5APNC',
    'ADMIN',
    NOW(),
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE mobile_number = '9000000001' AND is_active = TRUE
);
