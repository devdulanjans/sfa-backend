-- Drop the FK from user_permissions.permission_key → permissions(key)
-- so the column can hold both module codes (MOD_*) and action keys (ACTION_*)
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'user_permissions'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'permission_key'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE user_permissions DROP CONSTRAINT ' || constraint_name;
    END IF;
END $$;

-- Module catalog (self-referencing for sub-modules)
CREATE TABLE IF NOT EXISTS system_modules (
    code        VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    url         VARCHAR(200),
    icon        VARCHAR(50),
    parent_code VARCHAR(50)  REFERENCES system_modules(code),
    sort_order  INT          NOT NULL DEFAULT 0
);

-- Seed top-level modules
INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_CUSTOMERS',    'Customers',       '/customers',            '👥', NULL, 10),
('MOD_PRODUCTS',     'Products',        '/products',             '📦', NULL, 20),
('MOD_PRICING',      'Pricing',         NULL,                    '💰', NULL, 30),
('MOD_ORDERS',       'Orders',          '/orders',               '📋', NULL, 40),
('MOD_INVOICES',     'Invoices',        '/invoices',             '🧾', NULL, 50),
('MOD_INVENTORY',    'Inventory',       NULL,                    '🏭', NULL, 60),
('MOD_POS',          'Point of Sale',   NULL,                    '🖥',  NULL, 70),
('MOD_RETURNS',      'Returns',         '/returns',              '↩',  NULL, 80),
('MOD_DAMAGES',      'Damages',         '/damages',              '⚠',  NULL, 90),
('MOD_REPORTS',      'Reports',         NULL,                    '📈', NULL, 100)
ON CONFLICT (code) DO NOTHING;

-- Seed sub-modules (inserted after parents due to FK)
INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_BATCH_PRICE',  'Batch Pricing',    '/pricing',              '💰', 'MOD_PRICING',    10),
('MOD_PROMOTIONS',   'Promotions',       '/pricing/promotions',   '🎯', 'MOD_PRICING',    20),
('MOD_INV_STOCK',    'Stock Levels',     '/inventory',            '🏭', 'MOD_INVENTORY',  10),
('MOD_INV_MOVES',    'Stock Movements',  '/inventory/movements',  '📉', 'MOD_INVENTORY',  20),
('MOD_POS_TERMINAL', 'POS Terminal',     '/pos',                  '🖥',  'MOD_POS',        10),
('MOD_POS_HISTORY',  'POS History',      '/pos/history',          '🧾', 'MOD_POS',        20),
('MOD_RPT_SALES',    'Sales Reports',    '/reports/sales',        '📈', 'MOD_REPORTS',    10),
('MOD_RPT_PERF',     'Performance',      '/reports/performance',  '🏆', 'MOD_REPORTS',    20)
ON CONFLICT (code) DO NOTHING;
