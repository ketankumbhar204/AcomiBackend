-- Discover filters and ranking look up registrations by the converted space.
-- PostgreSQL does not index a foreign key column unless we ask it to.

CREATE INDEX IF NOT EXISTS idx_property_registrations_converted_space
    ON property_registrations (converted_space_id);

CREATE INDEX IF NOT EXISTS idx_mess_registrations_converted_space
    ON mess_registrations (converted_space_id);
