-- Fill blank profile email from the newest saved enquiry address.
UPDATE users
SET email = LOWER(TRIM(enquiry_emails ->> 0))
WHERE (email IS NULL OR TRIM(email) = '')
  AND jsonb_typeof(enquiry_emails) = 'array'
  AND jsonb_array_length(enquiry_emails) > 0
  AND NULLIF(TRIM(enquiry_emails ->> 0), '') IS NOT NULL;
