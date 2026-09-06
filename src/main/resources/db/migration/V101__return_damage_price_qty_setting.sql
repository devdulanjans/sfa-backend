INSERT INTO system_settings (key, value, description) VALUES
('return_damage_price_qty_enabled', 'true', 'When true (default), the mobile app shows each product''s unit price and line/total value when creating a return or damage request. When false, only the product name and quantity input are shown.')
ON CONFLICT (key) DO NOTHING;
