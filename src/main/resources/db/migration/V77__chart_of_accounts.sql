CREATE TABLE chart_of_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code      VARCHAR(20) NOT NULL UNIQUE,
    account_name      VARCHAR(150) NOT NULL,
    account_type      VARCHAR(20) NOT NULL,
    parent_account_id UUID REFERENCES chart_of_accounts(id),
    is_system_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coa_parent ON chart_of_accounts(parent_account_id);

-- System accounts (is_system_account = TRUE) back the automated postings in
-- JournalEntryService/InvoiceService/VendorBillService and cannot be deleted or
-- deactivated (see ChartOfAccountService). "Sales Discounts" is a contra-revenue
-- account used so invoice-issued postings never net subtotal-discount into a
-- single (possibly negative) credit line.
INSERT INTO chart_of_accounts (account_code, account_name, account_type, is_system_account) VALUES
('1100', 'Accounts Receivable',    'ASSET',     TRUE),
('2100', 'Accounts Payable',       'LIABILITY', TRUE),
('4000', 'Sales Revenue',          'REVENUE',   TRUE),
('4900', 'Sales Discounts',        'REVENUE',   TRUE),
('2200', 'Tax Payable',            'LIABILITY', TRUE),
('3900', 'Opening Balance Equity', 'EQUITY',    TRUE),
('5100', 'Purchases',              'EXPENSE',   TRUE),
('5200', 'Rent',                   'EXPENSE',   TRUE),
('5300', 'Utilities',              'EXPENSE',   TRUE),
('5400', 'Salaries',               'EXPENSE',   TRUE),
('5500', 'Transport',              'EXPENSE',   TRUE),
('5600', 'Marketing',              'EXPENSE',   TRUE),
('5700', 'Maintenance',            'EXPENSE',   TRUE),
('5900', 'Other Expenses',         'EXPENSE',   TRUE);
