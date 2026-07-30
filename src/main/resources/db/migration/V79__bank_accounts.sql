-- Generates account_code values (1101, 1102, ...) for the chart-of-accounts row
-- BankAccountService creates alongside each new bank account.
CREATE SEQUENCE IF NOT EXISTS bank_gl_account_code_seq START WITH 1 INCREMENT BY 1;

-- No current_balance column: a bank account's balance is always computed live as
-- SUM(debit-credit) over journal_entry_lines for its linked gl_account_id — the
-- ledger is the single source of truth, so there's nothing here to drift.
CREATE TABLE bank_accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_name   VARCHAR(150) NOT NULL,
    account_number VARCHAR(50),
    bank_name      VARCHAR(150),
    currency       VARCHAR(3) NOT NULL DEFAULT 'LKR',
    opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    gl_account_id  UUID NOT NULL UNIQUE REFERENCES chart_of_accounts(id),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
