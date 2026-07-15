INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_REPORTS', 'Reports', '/pos/reports', '📄', 'MOD_POS', 35)
ON CONFLICT (code) DO NOTHING;
