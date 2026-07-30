INSERT INTO system_settings (key, value, description) VALUES
('invoice_print_preview_enabled', 'false', 'When true, tapping Print Invoice on the mobile app shows a thermal receipt preview before printing. When false (default), it prints directly to the Bluetooth printer with no preview. Intended for debugging only.')
ON CONFLICT (key) DO NOTHING;
