-- Company Profile becomes per-channel: each tenant has its own name, logo, address,
-- tax/bank details — the existing row is backfilled into the seed ICEMAN channel.
ALTER TABLE company_profile ADD COLUMN tenant_id UUID;
UPDATE company_profile SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE company_profile ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE company_profile ADD CONSTRAINT fk_company_profile_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE company_profile ADD CONSTRAINT uk_company_profile_tenant UNIQUE (tenant_id);
