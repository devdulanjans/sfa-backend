-- Legacy customer records had "ICE TUBE" baked into the location field from
-- the old data source (mirrors the same cleanup applied in the admin app's
-- Customers list display). Strip it out here too, along with whatever
-- separator was left dangling next to it, so the raw data matches what's
-- shown in the UI.
UPDATE customers
SET location = NULLIF(
    TRIM(BOTH ' ,-/' FROM
        REGEXP_REPLACE(
            REGEXP_REPLACE(location, 'ice\s*tube', '', 'gi'),
            '\s{2,}', ' ', 'g'
        )
    ),
    ''
)
WHERE location ~* 'ice\s*tube';
