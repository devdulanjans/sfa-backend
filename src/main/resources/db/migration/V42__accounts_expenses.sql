CREATE TABLE expenses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category     VARCHAR(30) NOT NULL,
    amount       DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    expense_date DATE NOT NULL,
    description  TEXT,
    recorded_by  UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_expenses_date ON expenses(expense_date);
CREATE INDEX idx_expenses_category ON expenses(category);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_ACCOUNTS',        'Accounts',        NULL,                     '📒', NULL,           75),
('MOD_ACC_EXPENSES',    'Expenses',        '/accounts/expenses',     '💸', 'MOD_ACCOUNTS', 10),
('MOD_ACC_LEDGER',      'General Ledger',  '/accounts/ledger',       '📖', 'MOD_ACCOUNTS', 20),
('MOD_ACC_PROFIT_LOSS', 'Profit & Loss',   '/accounts/profit-loss',  '📊', 'MOD_ACCOUNTS', 30)
ON CONFLICT (code) DO NOTHING;
