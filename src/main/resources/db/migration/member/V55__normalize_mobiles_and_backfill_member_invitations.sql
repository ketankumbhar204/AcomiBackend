-- Normalize Indian mobile numbers to 10 digits across invitation-related tables.

CREATE OR REPLACE FUNCTION amico_normalize_mobile(raw TEXT)

RETURNS TEXT

LANGUAGE plpgsql

AS $$

DECLARE

    digits TEXT;

BEGIN

    digits := regexp_replace(raw, '\D', '', 'g');

    IF length(digits) = 12 AND left(digits, 2) = '91' THEN

        digits := substring(digits FROM 3);

    END IF;

    IF length(digits) = 10 THEN

        RETURN digits;

    END IF;

    RETURN raw;

END;

$$;



UPDATE users

SET mobile_number = amico_normalize_mobile(mobile_number),

    updated_at = NOW()

WHERE mobile_number IS NOT NULL

  AND mobile_number <> amico_normalize_mobile(mobile_number);



UPDATE members

SET mobile_number = amico_normalize_mobile(mobile_number),

    updated_at = NOW()

WHERE mobile_number IS NOT NULL

  AND mobile_number <> amico_normalize_mobile(mobile_number);



UPDATE invitations

SET mobile_number = amico_normalize_mobile(mobile_number),

    updated_at = NOW()

WHERE mobile_number IS NOT NULL

  AND mobile_number <> amico_normalize_mobile(mobile_number);



-- Backfill pending invitations for active members who were added directly (no app link yet).

INSERT INTO invitations (

    id,

    space_id,

    invited_by_user_id,

    mobile_number,

    role,

    status,

    expires_at,

    created_at,

    updated_at

)

SELECT

    gen_random_uuid(),

    m.space_id,

    s.owner_id,

    m.mobile_number,

    m.role,

    'PENDING',

    NOW() + INTERVAL '7 days',

    NOW(),

    NOW()

FROM members m

JOIN spaces s ON s.id = m.space_id

WHERE m.is_active = TRUE

  AND m.user_id IS NULL

  AND m.role <> 'OWNER'

  AND NOT EXISTS (

      SELECT 1

      FROM invitations i

      WHERE i.space_id = m.space_id

        AND i.mobile_number = m.mobile_number

        AND i.status = 'PENDING'

  );



DROP FUNCTION amico_normalize_mobile(TEXT);

