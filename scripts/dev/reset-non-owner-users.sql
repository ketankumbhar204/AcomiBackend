-- Dev-only reset: remove all user accounts except space owners.
-- Keeps spaces, member master records, and owner memberships intact.
-- Run manually: psql -d amico_db -f scripts/dev/reset-non-owner-users.sql

BEGIN;

CREATE TEMP TABLE keep_users ON COMMIT DROP AS
SELECT DISTINCT owner_id AS user_id
FROM spaces
WHERE owner_id IS NOT NULL;

-- Detach non-owner members from app accounts (member records remain for re-invite).
UPDATE members
SET user_id = NULL,
    membership_id = NULL,
    updated_at = NOW()
WHERE user_id IS NOT NULL
  AND user_id NOT IN (SELECT user_id FROM keep_users);

-- Remove app memberships for non-owners.
DELETE FROM space_memberships
WHERE user_id NOT IN (SELECT user_id FROM keep_users);

-- Remove invitations that are not owned by a kept inviter (safety).
DELETE FROM invitations
WHERE invited_by_user_id NOT IN (SELECT user_id FROM keep_users);

-- Occupancy audit fields referencing non-owners.
UPDATE occupancies
SET allocated_by = NULL,
    vacated_by = NULL,
    created_by = (SELECT user_id FROM keep_users LIMIT 1),
    updated_by = (SELECT user_id FROM keep_users LIMIT 1)
WHERE created_by IS NOT NULL
  AND created_by NOT IN (SELECT user_id FROM keep_users);

UPDATE occupancy_history
SET performed_by = (SELECT user_id FROM keep_users LIMIT 1)
WHERE performed_by IS NOT NULL
  AND performed_by NOT IN (SELECT user_id FROM keep_users);

UPDATE member_notes
SET created_by = (SELECT user_id FROM keep_users LIMIT 1)
WHERE created_by IS NOT NULL
  AND created_by NOT IN (SELECT user_id FROM keep_users);

UPDATE member_history
SET changed_by = (SELECT user_id FROM keep_users LIMIT 1)
WHERE changed_by IS NOT NULL
  AND changed_by NOT IN (SELECT user_id FROM keep_users);

UPDATE meal_participation_history
SET changed_by = (SELECT user_id FROM keep_users LIMIT 1)
WHERE changed_by IS NOT NULL
  AND changed_by NOT IN (SELECT user_id FROM keep_users);

DELETE FROM users
WHERE id NOT IN (SELECT user_id FROM keep_users);

COMMIT;
