-- Android (and optional WEB) enquiries can be created without an email.
-- Owner contact is delivered in-app; email is only required for explicit email delivery.
ALTER TABLE space_enquiries
    ALTER COLUMN requester_email DROP NOT NULL;
