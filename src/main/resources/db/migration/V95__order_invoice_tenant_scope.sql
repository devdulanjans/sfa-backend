-- Orders and invoices become per-channel, same treatment as Products/Customers/
-- Company Profile/Batch Prices/Promotions — backfilled into the seed ICEMAN channel
-- like everything before it. This is what lets invoice printing resolve the printed
-- company details (name/address/TIN/registration/bank) from the order's OWN channel
-- instead of whichever channel the printing user's session happens to be in.
ALTER TABLE orders ADD COLUMN tenant_id UUID;
UPDATE orders SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE orders ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_orders_tenant ON orders(tenant_id);

ALTER TABLE invoices ADD COLUMN tenant_id UUID;
UPDATE invoices SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE invoices ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
