-- Seed system roles
INSERT INTO roles (id, name, description, is_system, permissions) VALUES
(uuid_generate_v4(), 'SUPER_ADMIN',   'Full system access',           TRUE, '{"all": true}'::jsonb),
(uuid_generate_v4(), 'SALES_MANAGER', 'Manage team, approve orders',  TRUE, '{"orders": true, "customers": true, "products": true, "reports": true}'::jsonb),
(uuid_generate_v4(), 'SALES_REP',     'Create orders and invoices',   TRUE, '{"orders": true, "customers": "read", "invoices": true}'::jsonb),
(uuid_generate_v4(), 'FINANCE_USER',  'View invoices and reports',    TRUE, '{"invoices": "read", "reports": true}'::jsonb);

-- Seed default units
INSERT INTO units (name, abbreviation) VALUES
('Each',       'EA'),
('Kilogram',   'KG'),
('Litre',      'L'),
('Box',        'BOX'),
('Carton',     'CTN'),
('Dozen',      'DOZ'),
('Piece',      'PC');

-- Seed super admin user (password: Admin@123 — CHANGE IN PRODUCTION)
-- BCrypt of 'Admin@123'
INSERT INTO users (username, email, password_hash, role_id, full_name, status)
SELECT
    'superadmin',
    'admin@sfasystem.com',
    '$2a$12$LFkHFzWxHvhxBUhJa7KSIOMsJqxTOv2TGFQWkN0U7VQFQ0W9YAndy',
    r.id,
    'System Administrator',
    'ACTIVE'
FROM roles r WHERE r.name = 'SUPER_ADMIN';
