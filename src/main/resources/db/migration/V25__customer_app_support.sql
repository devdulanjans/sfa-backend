-- Customer self-service app support
-- Adds: customer link on users, order source tracking, CUSTOMER role

-- 1. Link a user account to a specific customer (for customer portal login)
ALTER TABLE users ADD COLUMN customer_id UUID REFERENCES customers(id);
CREATE INDEX idx_users_customer_id ON users(customer_id);

-- 2. Track where each order was placed from
ALTER TABLE orders ADD COLUMN order_source VARCHAR(20) NOT NULL DEFAULT 'SALES_REP';

-- 3. Seed the CUSTOMER role
INSERT INTO roles (id, name, description, is_system, permissions)
SELECT gen_random_uuid(), 'CUSTOMER', 'Customer self-service portal access', true, '{}'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CUSTOMER');
