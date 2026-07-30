INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_FIN',            'Financial Management', NULL,                       '💰', NULL,     76),
('MOD_FIN_LEDGER',     'General Ledger',        '/finance/general-ledger',  '📖', 'MOD_FIN', 10),
('MOD_FIN_PAYABLE',    'Accounts Payable',      '/finance/payable',         '📤', 'MOD_FIN', 20),
('MOD_FIN_RECEIVABLE', 'Accounts Receivable',   '/finance/receivable',      '📥', 'MOD_FIN', 30),
('MOD_FIN_BANK',       'Bank Management',       '/finance/bank',            '🏦', 'MOD_FIN', 40),
('MOD_FIN_TAX',        'Tax Management',        '/finance/tax',             '🧾', 'MOD_FIN', 50),
('MOD_FIN_ASSETS',     'Fixed Assets',          '/finance/fixed-assets',    '🏢', 'MOD_FIN', 60),
('MOD_FIN_PL',         'Profit & Loss',         '/finance/profit-loss',     '📊', 'MOD_FIN', 70),
('MOD_FIN_BS',         'Balance Sheet',         '/finance/balance-sheet',   '⚖️', 'MOD_FIN', 80)
ON CONFLICT (code) DO NOTHING;
