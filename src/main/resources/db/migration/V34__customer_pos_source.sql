-- Distinguish customers created quickly at POS (billing-only) from normal admin-created customers
ALTER TABLE customers ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

-- Drives auto-generated customer codes for POS-created customers (e.g. JOHN-0001)
CREATE SEQUENCE IF NOT EXISTS customer_pos_seq START WITH 1 INCREMENT BY 1;

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_CUSTOMERS', 'Customers', '/pos/customers', '👤', 'MOD_POS', 25)
ON CONFLICT (code) DO NOTHING;
