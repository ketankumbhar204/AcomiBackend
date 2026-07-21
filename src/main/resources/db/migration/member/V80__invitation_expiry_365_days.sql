-- Extend invitation lifetime to 365 days for all currently pending invites.
UPDATE invitations
SET expires_at = NOW() + INTERVAL '365 days',
    updated_at = NOW()
WHERE status = 'PENDING';
