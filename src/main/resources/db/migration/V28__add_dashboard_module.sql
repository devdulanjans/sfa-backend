INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order)
VALUES ('MOD_DASHBOARD', 'Dashboard', '/dashboard', '🏠', NULL, 0)
ON CONFLICT (code) DO NOTHING;
