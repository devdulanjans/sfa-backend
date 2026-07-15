CREATE TABLE company_profile (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name             VARCHAR(200) NOT NULL,
    logo_object_path         VARCHAR(300),
    logo_content_type        VARCHAR(100),
    registered_address       TEXT,
    operating_address        TEXT,
    phone                    VARCHAR(50),
    email                    VARCHAR(150),
    website                  VARCHAR(200),
    tax_id                   VARCHAR(100),
    vat_registration_number  VARCHAR(100),
    vat_rate_pct             NUMERIC(5,2) NOT NULL DEFAULT 0,
    bank_name                VARCHAR(150),
    bank_account_name        VARCHAR(150),
    bank_account_number      VARCHAR(100),
    bank_branch              VARCHAR(150),
    bank_swift_code          VARCHAR(50),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by               UUID
);

-- Seed with the values currently hardcoded in application.properties, so invoice
-- branding doesn't silently change the moment this ships — the admin can edit from here.
INSERT INTO company_profile (
    company_name, registered_address, operating_address, phone, email,
    tax_id, vat_rate_pct, bank_name, bank_account_name, bank_account_number, bank_branch
) VALUES (
    'Iceman Cold Chain Services (Pvt) Ltd',
    'No.43, St. Theresa''s Road, Ragalla, Kandana',
    'No.60/2, Aniyakanda Estate, Aniyakanda, Nagoda, Kandana',
    '0112059224', 'sales@iceman.lk',
    '114372315-7000', 18.00,
    'Commercial Bank of Ceylon PLC', 'Iceman Cold Chain Services (Pvt) Ltd', '1000973103', 'Kandana Branch'
);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_SETTINGS_COMPANY_PROFILE', 'Company Profile', '/settings/company-profile', '🏢', 'MOD_SETTINGS', 5)
ON CONFLICT (code) DO NOTHING;
