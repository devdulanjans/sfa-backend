-- Leads: potential customers captured in the field (typically by a sales rep, with GPS
-- coordinates). A lead is either converted into a real Customer automatically on create
-- (system setting lead_auto_convert_to_customer = 'true') or waits in PENDING until a user
-- holding ACTION_CONVERT_LEAD (e.g. an ASM/RSM) converts or rejects it.
CREATE SEQUENCE IF NOT EXISTS lead_number_seq START 1;

CREATE TABLE leads (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL REFERENCES tenants(id),
    lead_number           VARCHAR(30)   NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    contact_person        VARCHAR(100),
    phone                 VARCHAR(20),
    email                 VARCHAR(120),
    location              VARCHAR(100),
    place_of_supplier     VARCHAR(150),
    address_line          VARCHAR(500),
    latitude              NUMERIC(10,7),
    longitude             NUMERIC(10,7),
    notes                 TEXT,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_by            UUID          REFERENCES users(id),
    converted_customer_id UUID          REFERENCES customers(id),
    reviewed_by           UUID          REFERENCES users(id),
    reviewed_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(500),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ,
    CONSTRAINT uk_leads_tenant_number UNIQUE (tenant_id, lead_number)
);
CREATE INDEX idx_leads_tenant     ON leads(tenant_id);
CREATE INDEX idx_leads_status     ON leads(status);
CREATE INDEX idx_leads_created_by ON leads(created_by);

INSERT INTO system_settings (key, value, description) VALUES
('lead_auto_convert_to_customer', 'false', 'When true, every lead created (e.g. by a sales rep from the mobile app) is immediately converted into a customer. When false (default), leads stay pending until a user with the "Convert Leads" permission approves the conversion.')
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_LEADS', 'Leads', '/leads', '🎯', NULL, 11)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (key, category, label, description) VALUES
('ACTION_CONVERT_LEAD', 'Actions', 'Convert Leads', 'Approve (convert into a customer) or reject leads created by sales reps')
ON CONFLICT (key) DO NOTHING;
