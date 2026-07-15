-- Add CASHIER role for point-of-sale operators
INSERT INTO roles (id, name, description, is_system, permissions)
VALUES (
    uuid_generate_v4(),
    'CASHIER',
    'Point-of-sale operator — POS terminal access only',
    TRUE,
    '{}'::jsonb
)
ON CONFLICT DO NOTHING;
