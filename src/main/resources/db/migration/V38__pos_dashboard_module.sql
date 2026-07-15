INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_DASHBOARD', 'Dashboard', '/pos/dashboard', '📊', 'MOD_POS', 5)
ON CONFLICT (code) DO NOTHING;
