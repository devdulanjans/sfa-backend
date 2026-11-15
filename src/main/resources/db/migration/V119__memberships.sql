-- One account, many roles. A membership is "this person, in this channel, as this role" — the
-- same account can be an owner in one business, a sales rep in another and a customer of a
-- third, and even hold several roles in the same business. A session runs as exactly one
-- membership (JWT "mid" claim); its role, customer link and permissions come from it.
-- users.role_id stays as the PLATFORM role (SUPER_ADMIN / PLATFORM_OWNER) or, for everyone
-- else, mirrors their primary membership for older screens and clients.

CREATE TABLE memberships (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role_id              UUID NOT NULL REFERENCES roles(id),
    -- CUSTOMER role: the customer row (in this channel) this person orders as.
    customer_id          UUID REFERENCES customers(id) ON DELETE SET NULL,
    -- DISTRIBUTOR role: the distributor this person represents.
    login_distributor_id UUID REFERENCES distributors(id) ON DELETE SET NULL,
    -- INVITED: an existing account was invited and hasn't accepted yet (no access until then).
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by           UUID REFERENCES users(id) ON DELETE SET NULL,
    invited_at           TIMESTAMPTZ,
    responded_at         TIMESTAMPTZ,
    last_used_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    CONSTRAINT uk_memberships_user_tenant_role UNIQUE (user_id, tenant_id, role_id),
    CONSTRAINT chk_memberships_status CHECK (status IN ('ACTIVE', 'INVITED', 'DECLINED', 'REVOKED'))
);
CREATE INDEX idx_memberships_user   ON memberships(user_id, status);
CREATE INDEX idx_memberships_tenant ON memberships(tenant_id, status);

-- Every existing channel user keeps exactly the access they have today: one membership per
-- channel they belong to, with their current role / customer / distributor link.
INSERT INTO memberships (user_id, tenant_id, role_id, customer_id, login_distributor_id, status, last_used_at)
SELECT u.id, ut.tenant_id, u.role_id, u.customer_id, u.login_distributor_id, 'ACTIVE', now()
  FROM users u
  JOIN user_tenants ut ON ut.user_id = u.id
  JOIN roles r ON r.id = u.role_id
 WHERE r.name NOT IN ('SUPER_ADMIN', 'PLATFORM_OWNER');

-- Customer-portal logins that never had a channel row: their customer's own channel.
INSERT INTO memberships (user_id, tenant_id, role_id, customer_id, status, last_used_at)
SELECT u.id, c.tenant_id, u.role_id, u.customer_id, 'ACTIVE', now()
  FROM users u
  JOIN roles r ON r.id = u.role_id AND r.name = 'CUSTOMER'
  JOIN customers c ON c.id = u.customer_id
 WHERE NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = u.id);

-- Distributor logins without a channel row: the first channel their distributor belongs to.
INSERT INTO memberships (user_id, tenant_id, role_id, login_distributor_id, status, last_used_at)
SELECT u.id, (SELECT dt.tenant_id FROM distributor_tenants dt WHERE dt.distributor_id = u.login_distributor_id LIMIT 1),
       u.role_id, u.login_distributor_id, 'ACTIVE', now()
  FROM users u
  JOIN roles r ON r.id = u.role_id AND r.name = 'DISTRIBUTOR'
 WHERE u.login_distributor_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = u.id)
   AND EXISTS (SELECT 1 FROM distributor_tenants dt WHERE dt.distributor_id = u.login_distributor_id);

-- Permissions move from the account to the membership, so the same person can have full
-- rights as an owner and limited rights as a rep. Old account-level rows (membership_id NULL)
-- remain as the fallback for sessions without a membership.
ALTER TABLE user_permissions ADD COLUMN membership_id UUID REFERENCES memberships(id) ON DELETE CASCADE;
ALTER TABLE user_permissions DROP CONSTRAINT IF EXISTS user_permissions_user_id_permission_key_key;
CREATE UNIQUE INDEX ux_user_permissions_account    ON user_permissions(user_id, permission_key) WHERE membership_id IS NULL;
CREATE UNIQUE INDEX ux_user_permissions_membership ON user_permissions(membership_id, permission_key) WHERE membership_id IS NOT NULL;
INSERT INTO user_permissions (user_id, permission_key, granted_by, granted_at, membership_id)
SELECT up.user_id, up.permission_key, up.granted_by, up.granted_at, m.id
  FROM user_permissions up
  JOIN memberships m ON m.user_id = up.user_id
 WHERE up.membership_id IS NULL;

-- Self sign-up on the website creates an account with no channel access until a business
-- invites it.
INSERT INTO roles (name, description, is_system)
SELECT 'ACCOUNT', 'Personal account with no business role yet', TRUE
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ACCOUNT');

-- One account per phone number too (stored normalised, 0XXXXXXXXX).
CREATE UNIQUE INDEX ux_users_phone ON users(phone) WHERE phone IS NOT NULL AND phone <> '';
