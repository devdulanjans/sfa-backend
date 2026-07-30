-- Fixed assets are grouped under one control account each (like Accounts Receivable /
-- Accounts Payable), with per-asset detail (cost, accumulated depreciation) tracked in
-- fixed_assets itself as the subsidiary ledger — not one chart-of-accounts row per asset.
INSERT INTO chart_of_accounts (account_code, account_name, account_type, is_system_account) VALUES
('1500', 'Fixed Assets',              'ASSET',  TRUE),
('1590', 'Accumulated Depreciation',  'ASSET',  TRUE),
('5800', 'Depreciation Expense',      'EXPENSE', TRUE)
ON CONFLICT (account_code) DO NOTHING;

CREATE TABLE fixed_assets (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_code               VARCHAR(30) NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    category                 VARCHAR(100),
    purchase_date            DATE NOT NULL,
    purchase_cost            NUMERIC(14,2) NOT NULL,
    salvage_value            NUMERIC(14,2) NOT NULL DEFAULT 0,
    useful_life_years        INT NOT NULL,
    accumulated_depreciation NUMERIC(14,2) NOT NULL DEFAULT 0,
    bank_account_id          UUID NOT NULL REFERENCES bank_accounts(id),
    journal_entry_id         UUID REFERENCES journal_entries(id),
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_by               UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One row per asset per period a depreciation run has posted for — the unique
-- constraint is what makes re-running "depreciation for March" idempotent.
CREATE TABLE fixed_asset_depreciation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fixed_asset_id   UUID NOT NULL REFERENCES fixed_assets(id),
    period_date      DATE NOT NULL,
    amount           NUMERIC(14,2) NOT NULL,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (fixed_asset_id, period_date)
);
