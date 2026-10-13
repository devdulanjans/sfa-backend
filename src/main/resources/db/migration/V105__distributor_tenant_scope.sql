-- Distributors become channel-aware — but unlike Customers/Products (which belong to exactly
-- one channel), a distributor can operate under several channels at once, and a channel can
-- have several distributors, so this is a many-to-many join table (same shape as user_tenants),
-- not a tenant_id column. Every existing distributor is backfilled into the seed ICEMAN channel.
CREATE TABLE distributor_tenants (
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL REFERENCES tenants(id)      ON DELETE CASCADE,
    PRIMARY KEY (distributor_id, tenant_id)
);
CREATE INDEX idx_distributor_tenants_tenant ON distributor_tenants(tenant_id);

INSERT INTO distributor_tenants (distributor_id, tenant_id)
SELECT d.id, (SELECT id FROM tenants WHERE code = 'ICEMAN')
FROM distributors d;

-- Distributor login (self-service "portal" account, same pattern as the customer portal
-- from V25__customer_app_support.sql) — a distributor's code stays globally unique (it's no
-- longer scoped to a single channel), so no change to distributors_code_key.
ALTER TABLE users ADD COLUMN login_distributor_id UUID REFERENCES distributors(id);
CREATE INDEX idx_users_login_distributor_id ON users(login_distributor_id);

INSERT INTO roles (id, name, description, is_system, permissions)
SELECT gen_random_uuid(), 'DISTRIBUTOR', 'Distributor self-service portal access', true, '{}'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'DISTRIBUTOR');
