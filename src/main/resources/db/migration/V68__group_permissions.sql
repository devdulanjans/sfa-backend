INSERT INTO permissions(key, category, label, description) VALUES
('MOD_CUSTOMER_GROUPS', 'Pages', 'Customer Groups', 'View and manage customer groups'),
('MOD_PRODUCT_GROUPS',  'Pages', 'Product Groups',  'View and manage product groups')
ON CONFLICT (key) DO NOTHING;
