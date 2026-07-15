INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_DAILY_REPORT', 'Daily Report', '/pos/daily-report', '📅', 'MOD_POS', 45)
ON CONFLICT (code) DO NOTHING;
