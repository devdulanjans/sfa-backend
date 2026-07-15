-- ── PLATFORM_OWNER role ──────────────────────────────────────────────────────
-- Sits above SUPER_ADMIN for exactly one purpose (this installation's SFA/POS
-- license toggle) and deliberately has no other module access. It must never
-- appear in the Users screen's role picker or be assignable through it — see
-- RoleController.list() and UserService's rejectPlatformOwnerAssignment().
INSERT INTO roles (id, name, description, is_system, permissions)
SELECT uuid_generate_v4(), 'PLATFORM_OWNER',
       'System owner — manages this installation''s SFA/POS license only.', TRUE, '{}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PLATFORM_OWNER');

-- ── License settings (singleton) ─────────────────────────────────────────────
CREATE TABLE license_settings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sfa_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pos_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    client_name VARCHAR(200),
    note        TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  UUID
);

-- Both packages ON by default so every existing install keeps working exactly
-- as it does today until the platform owner explicitly changes something.
INSERT INTO license_settings (sfa_enabled, pos_enabled, client_name)
VALUES (TRUE, TRUE, 'Default');

-- ── Seed exactly one PLATFORM_OWNER user, intentionally locked ───────────────
-- The hash below is a real BCrypt hash of a random value that was generated
-- once, printed nowhere except into this hash, and discarded — this account
-- cannot be logged into with any known password. It stays locked until
-- PlatformOwnerInitializer (sfa-backend/src/main/java/com/sfa/config) sets a
-- real password from the PLATFORM_OWNER_PASSWORD environment variable on
-- boot. This is deliberate: unlike 'superadmin' (V2__seed_roles_and_admin.sql),
-- this migration ships to every client install, and the role sits above that
-- client's own Super Admin — a shared, known password here would mean every
-- install carries the same master credential.
INSERT INTO users (username, email, password_hash, role_id, full_name, status)
SELECT
    'platformowner',
    'owner@platform.internal',
    '$2a$12$REM.w/7SXt7RvD5ymVuXo.3pubpLZh23jBnkyIraH3rXhUVp1ymdG',
    r.id,
    'Platform Owner',
    'ACTIVE'
FROM roles r
WHERE r.name = 'PLATFORM_OWNER'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'platformowner');
