-- Re-seeds the superadmin user if it's missing. Needed because V2's INSERT only
-- ever runs once per database — if a database's users table is later cleared,
-- flyway_schema_history still shows V2 as applied, so the row never comes back.
-- Same credentials as originally seeded in V2/V6 (username: superadmin, password: Admin@123).
INSERT INTO users (username, email, password_hash, role_id, full_name, status)
SELECT
    'superadmin',
    'admin@sfasystem.com',
    '$2a$12$UiqzZcv63C.DqsmbqA4MqepDDX6B7kAjjurgOMvazkzGs6sjN8T.6',
    r.id,
    'System Administrator',
    'ACTIVE'
FROM roles r
WHERE r.name = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'superadmin');
