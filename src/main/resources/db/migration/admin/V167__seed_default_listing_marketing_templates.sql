-- Seed editable default promotional templates (formerly client-only built-ins).

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000001'::uuid,
    'General property promotion',
    'Default property promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} on ACOMI.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'General property promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000002'::uuid,
    'PG promotion',
    'Default PG promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} (PG) on ACOMI to people searching for paying guest stays.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    20,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'PG promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000003'::uuid,
    'Co-living promotion',
    'Default co-living promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} as a co-living stay on ACOMI.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    30,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'Co-living promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000004'::uuid,
    'Hostel promotion',
    'Default hostel promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} (Hostel) on ACOMI.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    40,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'Hostel promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000005'::uuid,
    'Rental promotion',
    'Default rental promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} as a rental listing on ACOMI.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    50,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'Rental promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000006'::uuid,
    'Corporate Stay promotion',
    'Default corporate stay promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{propertyName}} for corporate stay enquiries on ACOMI.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'PROPERTY',
    TRUE,
    60,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'Corporate Stay promotion'
);

INSERT INTO admin_listing_marketing_templates (
    id, name, description, body, applicability, active, display_order, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-000000000007'::uuid,
    'Mess promotion',
    'Default mess promotion template',
    E'Hi {{ownerName}},\n\nWe''d like to promote {{messName}} on ACOMI to nearby residents looking for mess meals.\nType: {{spaceType}}\nLocation: {{location}}\nAddress: {{address}}\nPrice: {{rent}}\n\nPlease share any updated photos, videos, or details you would like us to use.',
    'MESS',
    TRUE,
    70,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admin_listing_marketing_templates WHERE name = 'Mess promotion'
);
