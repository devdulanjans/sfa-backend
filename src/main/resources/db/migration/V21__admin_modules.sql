-- Add admin-section modules so every sidebar item is permission-controlled
INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
-- Standalone
('MOD_DISTRIBUTORS',        'Distributors',       '/distributors',          '🏢', NULL,              110),
-- Users & Admin group
('MOD_ADMIN_USERS',         'Users & Admin',      NULL,                     '👤', NULL,              120),
('MOD_USER_LIST',           'User List',          '/users',                 '👤', 'MOD_ADMIN_USERS',  10),
('MOD_USER_PERMS',          'Permissions',        '/users/permissions',     '🔑', 'MOD_ADMIN_USERS',  20),
('MOD_USER_ACTIVITY',       'Activity Log',       '/users/activity-log',    '📝', 'MOD_ADMIN_USERS',  30),
-- Settings group
('MOD_SETTINGS',            'Settings',           NULL,                     '⚙',  NULL,              130),
('MOD_SETTINGS_GENERAL',    'General Settings',   '/settings/general',      '⚙',  'MOD_SETTINGS',     10),
('MOD_SETTINGS_CATEGORIES', 'Categories',         '/settings/categories',   '🏷', 'MOD_SETTINGS',     20),
('MOD_SETTINGS_UNITS',      'Units of Measure',   '/settings/units',        '📐', 'MOD_SETTINGS',     30)
ON CONFLICT (code) DO NOTHING;
