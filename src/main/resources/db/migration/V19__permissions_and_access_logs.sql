-- Permission catalog (immutable definitions)
CREATE TABLE permissions (
    key         VARCHAR(100) PRIMARY KEY,
    category    VARCHAR(50)  NOT NULL,
    label       VARCHAR(200) NOT NULL,
    description TEXT
);

-- Which permissions each user has been granted
CREATE TABLE user_permissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_key  VARCHAR(100) NOT NULL REFERENCES permissions(key) ON DELETE CASCADE,
    granted_by      UUID        NOT NULL REFERENCES users(id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, permission_key)
);

CREATE INDEX idx_user_permissions_user ON user_permissions(user_id);

-- Access / activity log  (everything that happens in the system)
CREATE TABLE access_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        REFERENCES users(id),
    username    VARCHAR(100),
    action      VARCHAR(100) NOT NULL,
    resource    VARCHAR(200),
    details     TEXT,
    ip_address  VARCHAR(50),
    status      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_logs_user       ON access_logs(user_id, created_at DESC);
CREATE INDEX idx_access_logs_action     ON access_logs(action, created_at DESC);
CREATE INDEX idx_access_logs_status     ON access_logs(status, created_at DESC);
CREATE INDEX idx_access_logs_created_at ON access_logs(created_at DESC);

-- ── Seed permission catalog ───────────────────────────────────────────────────
INSERT INTO permissions(key, category, label, description) VALUES
-- Pages
('PAGE_DASHBOARD',   'Pages', 'Dashboard',       'View the main dashboard'),
('PAGE_CUSTOMERS',   'Pages', 'Customers',        'View and manage customers'),
('PAGE_PRODUCTS',    'Pages', 'Products',         'View and manage products'),
('PAGE_PRICING',     'Pages', 'Batch Pricing',    'View and manage batch prices'),
('PAGE_PROMOTIONS',  'Pages', 'Promotions',       'View and manage promotions'),
('PAGE_ORDERS',      'Pages', 'Orders',           'View and process orders'),
('PAGE_INVOICES',    'Pages', 'Invoices',         'View invoices'),
('PAGE_RETURNS',     'Pages', 'Returns',          'View and manage returns'),
('PAGE_DAMAGES',     'Pages', 'Damages',          'View and manage damage reports'),
('PAGE_REPORTS',     'Pages', 'Reports',          'View sales and performance reports'),
('PAGE_INVENTORY',   'Pages', 'Inventory',        'View and manage stock levels'),
('PAGE_POS',         'Pages', 'POS Terminal',     'Access the point-of-sale terminal'),
('PAGE_USERS',       'Pages', 'Users',            'View and manage user accounts'),
('PAGE_DISTRIBUTORS','Pages', 'Distributors',     'View and manage distributors'),
('PAGE_SETTINGS',    'Pages', 'Settings',         'Access system settings'),
-- Actions
('ACTION_APPROVE_ORDER',  'Actions', 'Approve Orders',      'Approve submitted orders'),
('ACTION_CANCEL_ORDER',   'Actions', 'Cancel Orders',       'Cancel any order'),
('ACTION_ADJUST_STOCK',   'Actions', 'Adjust Stock',        'Manually adjust inventory stock levels'),
('ACTION_VOID_POS_SALE',  'Actions', 'Void POS Sales',      'Void a completed POS sale'),
('ACTION_MANAGE_PROMOS',  'Actions', 'Manage Promotions',   'Create and edit promotions'),
('ACTION_EXPORT_REPORTS', 'Actions', 'Export Reports',      'Export reports to CSV/PDF'),
('ACTION_MANAGE_PRICING', 'Actions', 'Manage Batch Pricing','Create and edit batch prices');
