-- Batch Prices and Promotions become per-channel, same treatment as Customers/Products/
-- Company Profile — backfilled into the seed ICEMAN channel like everything before it.
ALTER TABLE batch_prices ADD COLUMN tenant_id UUID;
UPDATE batch_prices SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE batch_prices ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE batch_prices ADD CONSTRAINT fk_batch_prices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_batch_prices_tenant ON batch_prices(tenant_id);

ALTER TABLE promotions ADD COLUMN tenant_id UUID;
UPDATE promotions SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE promotions ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE promotions ADD CONSTRAINT fk_promotions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_promotions_tenant ON promotions(tenant_id);
