-- Customer groups and product groups become channel-specific (public SaaS: each business
-- sees only its own groups). Existing groups are given the channel most of their members
-- belong to; a group with no members falls back to the channel of a price rule or
-- promotion that uses it, then to the oldest channel.

ALTER TABLE customer_groups ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE product_groups  ADD COLUMN tenant_id UUID REFERENCES tenants(id);

UPDATE customer_groups g SET tenant_id = (
    SELECT c.tenant_id FROM customer_group_members m JOIN customers c ON c.id = m.customer_id
     WHERE m.customer_group_id = g.id AND c.tenant_id IS NOT NULL
     GROUP BY c.tenant_id ORDER BY count(*) DESC, c.tenant_id LIMIT 1);
UPDATE customer_groups g SET tenant_id = COALESCE(
    (SELECT p.tenant_id FROM promotions p WHERE p.customer_group_id = g.id AND p.tenant_id IS NOT NULL LIMIT 1),
    (SELECT b.tenant_id FROM batch_prices b WHERE b.customer_group_id = g.id AND b.tenant_id IS NOT NULL LIMIT 1),
    (SELECT t.id FROM tenants t ORDER BY t.created_at, t.id LIMIT 1))
 WHERE g.tenant_id IS NULL;

UPDATE product_groups g SET tenant_id = (
    SELECT pr.tenant_id FROM product_group_members m JOIN products pr ON pr.id = m.product_id
     WHERE m.product_group_id = g.id AND pr.tenant_id IS NOT NULL
     GROUP BY pr.tenant_id ORDER BY count(*) DESC, pr.tenant_id LIMIT 1);
UPDATE product_groups g SET tenant_id = COALESCE(
    (SELECT p.tenant_id FROM promotions p WHERE p.product_group_id = g.id AND p.tenant_id IS NOT NULL LIMIT 1),
    (SELECT b.tenant_id FROM batch_prices b WHERE b.product_group_id = g.id AND b.tenant_id IS NOT NULL LIMIT 1),
    (SELECT t.id FROM tenants t ORDER BY t.created_at, t.id LIMIT 1))
 WHERE g.tenant_id IS NULL;

ALTER TABLE customer_groups ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE product_groups  ALTER COLUMN tenant_id SET NOT NULL;

-- Names are unique within a channel, not across the whole platform.
ALTER TABLE customer_groups DROP CONSTRAINT IF EXISTS customer_groups_name_key;
ALTER TABLE product_groups  DROP CONSTRAINT IF EXISTS product_groups_name_key;
ALTER TABLE customer_groups ADD CONSTRAINT uk_customer_groups_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE product_groups  ADD CONSTRAINT uk_product_groups_tenant_name  UNIQUE (tenant_id, name);

CREATE INDEX idx_customer_groups_tenant ON customer_groups(tenant_id);
CREATE INDEX idx_product_groups_tenant  ON product_groups(tenant_id);
