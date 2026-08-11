-- Hard-delete spaces by name filter (dev/admin only).
-- Default filter: names containing "Test Aaaaaa" (case-insensitive).
--
-- Usage (psql):
--   psql -h localhost -U postgres -d amico_db -v ON_ERROR_STOP=1 -f scripts/dev/hard-delete-spaces-by-name.sql
-- Override filter:
--   psql ... -v name_pattern='%My Test Space%' -f scripts/dev/hard-delete-spaces-by-name.sql
--
-- Dry-run (select only): set dry_run=1
--   psql ... -v dry_run=1 -f scripts/dev/hard-delete-spaces-by-name.sql

\if :{?name_pattern}
\else
\set name_pattern '%Test Aaaaaa%'
\endif

\if :{?dry_run}
\else
\set dry_run 0
\endif

BEGIN;

CREATE TEMP TABLE _hard_delete_spaces ON COMMIT DROP AS
SELECT id, name, type, is_active, created_at
FROM spaces
WHERE name ILIKE :'name_pattern';

SELECT 'BEFORE matching spaces' AS step, COUNT(*) AS cnt FROM _hard_delete_spaces;
SELECT id, name, type, is_active, created_at FROM _hard_delete_spaces ORDER BY created_at, name;

\if :dry_run
ROLLBACK;
\echo 'Dry run complete — rolled back; no rows deleted.'
\else

-- ---------------------------------------------------------------------------
-- Payments / notifications / complaints
-- ---------------------------------------------------------------------------
DELETE FROM space_payment_timeline_events
WHERE payment_id IN (
    SELECT id FROM space_payments WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM space_payments
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_payment_member_month
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_payment_month_summary
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM payment_reference_counters
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_complaint_comments
WHERE complaint_id IN (
    SELECT id FROM space_complaints WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM space_complaints
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_notifications
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- ---------------------------------------------------------------------------
-- Meal polls / day payments / wallet ledger
-- ---------------------------------------------------------------------------
DELETE FROM meal_poll_payment_events
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_poll_day_payments
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM member_meal_balance_ledger
WHERE balance_id IN (
    SELECT id FROM member_meal_balances WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
)
OR poll_id IN (
    SELECT id FROM meal_polls WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

-- poll children (responses/options/delivery) CASCADE from meal_polls, but delete explicitly
DELETE FROM meal_poll_responses
WHERE poll_id IN (SELECT id FROM meal_polls WHERE space_id IN (SELECT id FROM _hard_delete_spaces));

DELETE FROM meal_poll_options
WHERE poll_id IN (SELECT id FROM meal_polls WHERE space_id IN (SELECT id FROM _hard_delete_spaces));

DELETE FROM meal_poll_member_delivery
WHERE poll_id IN (SELECT id FROM meal_polls WHERE space_id IN (SELECT id FROM _hard_delete_spaces));

DELETE FROM meal_polls
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM member_meal_balances
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM subscription_activation_requests
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_billing_change_requests
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM subscription_plans
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- ---------------------------------------------------------------------------
-- Daily menus / combos / plans / catalog settings
-- ---------------------------------------------------------------------------
DELETE FROM daily_menu_package_items
WHERE entry_id IN (
    SELECT e.id
    FROM daily_menu_entries e
    JOIN daily_menus m ON m.id = e.daily_menu_id
    WHERE m.space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM daily_menu_entries
WHERE daily_menu_id IN (
    SELECT id FROM daily_menus WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM daily_menus
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_combo_items
WHERE combo_id IN (
    SELECT id FROM meal_combos WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM meal_combos
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_participation_history
WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
   OR participation_id IN (
        SELECT id FROM meal_participations WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
   );

DELETE FROM meal_participation_last_delivery
WHERE participation_id IN (
    SELECT id FROM meal_participations WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM meal_participation_delivery_allowed
WHERE participation_id IN (
    SELECT id FROM meal_participations WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM meal_participation_delivery_default
WHERE participation_id IN (
    SELECT id FROM meal_participations WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM meal_participations
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_plans
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM meal_delivery_locations
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_food_item_settings
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_food_category_settings
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- Space-scoped catalog only (never touch GLOBAL rows with space_id IS NULL)
DELETE FROM food_items
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM food_categories
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- ---------------------------------------------------------------------------
-- Occupancy / accommodation tree
-- ---------------------------------------------------------------------------
DELETE FROM occupancy_amenities
WHERE occupancy_id IN (
    SELECT id FROM occupancies WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM occupancy_charge_snapshots
WHERE occupancy_id IN (
    SELECT id FROM occupancies WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM occupancy_history
WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
   OR occupancy_id IN (
        SELECT id FROM occupancies WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
   );

DELETE FROM occupancies
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM beds
WHERE room_id IN (
    SELECT r.id
    FROM rooms r
    JOIN floors f ON f.id = r.floor_id
    JOIN buildings b ON b.id = f.building_id
    WHERE b.space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM rooms
WHERE floor_id IN (
    SELECT f.id
    FROM floors f
    JOIN buildings b ON b.id = f.building_id
    WHERE b.space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM units
WHERE building_id IN (
    SELECT id FROM buildings WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
)
OR floor_id IN (
    SELECT f.id
    FROM floors f
    JOIN buildings b ON b.id = f.building_id
    WHERE b.space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM floors
WHERE building_id IN (
    SELECT id FROM buildings WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM accommodation_setup_idempotency
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM buildings
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_amenities
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- ---------------------------------------------------------------------------
-- Members / memberships / invitations
-- ---------------------------------------------------------------------------
DELETE FROM member_documents
WHERE member_id IN (
    SELECT id FROM members WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM member_notes
WHERE member_id IN (
    SELECT id FROM members WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM member_history
WHERE member_id IN (
    SELECT id FROM members WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
);

DELETE FROM members
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM space_memberships
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

DELETE FROM invitations
WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

-- ---------------------------------------------------------------------------
-- Spaces (hard delete)
-- ---------------------------------------------------------------------------
DELETE FROM spaces
WHERE id IN (SELECT id FROM _hard_delete_spaces);

SELECT 'AFTER matching spaces' AS step, COUNT(*) AS cnt
FROM spaces
WHERE name ILIKE :'name_pattern';

-- Orphan checks for deleted IDs
SELECT 'orphan_space_memberships' AS check_name, COUNT(*) AS cnt
FROM space_memberships WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_members', COUNT(*) FROM members WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_buildings', COUNT(*) FROM buildings WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_meal_plans', COUNT(*) FROM meal_plans WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_meal_combos', COUNT(*) FROM meal_combos WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_space_amenities', COUNT(*) FROM space_amenities WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_space_notifications', COUNT(*) FROM space_notifications WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_space_payments', COUNT(*) FROM space_payments WHERE space_id IN (SELECT id FROM _hard_delete_spaces)
UNION ALL
SELECT 'orphan_invitations', COUNT(*) FROM invitations WHERE space_id IN (SELECT id FROM _hard_delete_spaces);

COMMIT;
\echo 'Hard delete committed.'
\endif
