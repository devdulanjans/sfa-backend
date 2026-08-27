CREATE TABLE reasons (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type            VARCHAR(20) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    allow_free_text BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (type, label)
);

-- Seed with the reasons that were previously hardcoded in the mobile app's
-- damage/return forms, so existing installs see no behavior change until an
-- admin edits the list from Settings -> Return & Damage Reasons.
INSERT INTO reasons (type, label, allow_free_text, sort_order) VALUES
('DAMAGE', 'Damaged in Transit',             FALSE, 10),
('DAMAGE', 'Damaged in Storage / Warehouse', FALSE, 20),
('DAMAGE', 'Expired',                        FALSE, 30),
('DAMAGE', 'Broken Packaging',               FALSE, 40),
('DAMAGE', 'Water / Moisture Damage',        FALSE, 50),
('DAMAGE', 'Mishandling',                    FALSE, 60),
('DAMAGE', 'Other',                          TRUE,  70),
('RETURN', 'Damaged in Transit',             FALSE, 10),
('RETURN', 'Expired / Near Expiry',          FALSE, 20),
('RETURN', 'Wrong Item Delivered',           FALSE, 30),
('RETURN', 'Quality Issue',                  FALSE, 40),
('RETURN', 'Excess Stock',                   FALSE, 50),
('RETURN', 'Customer Changed Mind',          FALSE, 60),
('RETURN', 'Other',                          TRUE,  70);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_SETTINGS_REASONS', 'Return & Damage Reasons', '/settings/reasons', '📋', 'MOD_SETTINGS', 40)
ON CONFLICT (code) DO NOTHING;
