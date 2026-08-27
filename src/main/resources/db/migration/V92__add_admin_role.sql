-- Channel-scoped administrator role — sits between SUPER_ADMIN (platform-wide) and
-- SALES_MANAGER (its permissions are inherited via the ROLE_ADMIN > ROLE_SALES_MANAGER
-- hierarchy in SecurityConfig, not duplicated here). Adds user management and Company
-- Profile editing within its own assigned channel(s) — see UserService/CompanyProfileController.
INSERT INTO roles (id, name, description, is_system, permissions)
SELECT gen_random_uuid(), 'ADMIN',
       'Channel administrator — manages one or more channels: everything a Sales Manager can do, plus users and Company Profile within those channels.',
       TRUE, '{}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');
