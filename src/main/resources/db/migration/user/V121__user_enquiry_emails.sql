-- Saved emails used on space enquiries. Newest-used first. Profile email is not overwritten.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS enquiry_emails JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE users u
SET enquiry_emails = sub.emails
FROM (
    SELECT requester_user_id AS user_id,
           jsonb_agg(email ORDER BY last_used DESC) AS emails
    FROM (
        SELECT requester_user_id,
               LOWER(TRIM(requester_email)) AS email,
               MAX(requested_at) AS last_used
        FROM space_enquiries
        WHERE requester_email IS NOT NULL
          AND TRIM(requester_email) <> ''
        GROUP BY requester_user_id, LOWER(TRIM(requester_email))
    ) distinct_emails
    GROUP BY requester_user_id
) sub
WHERE u.id = sub.user_id;

UPDATE users
SET enquiry_emails = enquiry_emails || jsonb_build_array(LOWER(TRIM(email)))
WHERE email IS NOT NULL
  AND TRIM(email) <> ''
  AND NOT (enquiry_emails @> jsonb_build_array(LOWER(TRIM(email))));
