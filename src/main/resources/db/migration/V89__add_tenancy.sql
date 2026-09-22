-- ============================================================
-- Multi-channel tenancy — Phase 0 (infrastructure) + Phase 1 pilot
-- (customers, products). Every existing row is backfilled into one
-- seed tenant ("ICEMAN") representing the business as it exists today,
-- so nothing changes for current users until a second channel is created
-- and staff/data are deliberately assigned to it.
-- ============================================================

-- ── Tenants (channels) ───────────────────────────────────────────────────────
CREATE TABLE tenants (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(30)  NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_tenants_code   ON tenants(code);
CREATE INDEX idx_tenants_status ON tenants(status);

INSERT INTO tenants (code, name, status) VALUES ('ICEMAN', 'Iceman', 'ACTIVE');

-- ── User ↔ tenant membership ─────────────────────────────────────────────────
CREATE TABLE user_tenants (
    user_id   UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tenant_id)
);

ALTER TABLE users
    ADD COLUMN default_tenant_id UUID REFERENCES tenants(id);

-- Every existing user except SUPER_ADMIN/PLATFORM_OWNER (who operate above any
-- single channel, per AuthService.UNSCOPED_ROLES) is assigned to the seed tenant.
INSERT INTO user_tenants (user_id, tenant_id)
SELECT u.id, t.id
FROM users u, tenants t
WHERE t.code = 'ICEMAN'
  AND u.role_id IN (SELECT id FROM roles WHERE name NOT IN ('SUPER_ADMIN', 'PLATFORM_OWNER'));

UPDATE users u
SET default_tenant_id = t.id
FROM tenants t
WHERE t.code = 'ICEMAN'
  AND u.role_id IN (SELECT id FROM roles WHERE name NOT IN ('SUPER_ADMIN', 'PLATFORM_OWNER'));

-- ── customers: scope to the seed tenant ──────────────────────────────────────
ALTER TABLE customers ADD COLUMN tenant_id UUID;
UPDATE customers SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE customers ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);

-- customer_code moves from globally unique to unique-per-channel — two channels
-- may legitimately reuse the same short code.
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_code_key;
ALTER TABLE customers ADD CONSTRAINT uk_customers_tenant_code UNIQUE (tenant_id, customer_code);

-- ── products: scope to the seed tenant ───────────────────────────────────────
ALTER TABLE products ADD COLUMN tenant_id UUID;
UPDATE products SET tenant_id = (SELECT id FROM tenants WHERE code = 'ICEMAN');
ALTER TABLE products ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_products_tenant ON products(tenant_id);

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_code_key;
ALTER TABLE products ADD CONSTRAINT uk_products_tenant_code UNIQUE (tenant_id, product_code);
