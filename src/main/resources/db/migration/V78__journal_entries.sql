CREATE SEQUENCE IF NOT EXISTS journal_entry_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE journal_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_number VARCHAR(20) NOT NULL UNIQUE,
    entry_date   DATE NOT NULL,
    description  TEXT,
    source_type  VARCHAR(30) NOT NULL,
    source_id    UUID,
    status       VARCHAR(15) NOT NULL DEFAULT 'POSTED',
    created_by   UUID REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_je_source ON journal_entries(source_type, source_id);
CREATE INDEX idx_je_date ON journal_entries(entry_date);

CREATE TABLE journal_entry_lines (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    account_id       UUID NOT NULL REFERENCES chart_of_accounts(id),
    debit_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    description      TEXT,
    line_order       INT NOT NULL DEFAULT 0
);

-- Every account-balance/ledger-drilldown query (bank balances, GL, AR, AP) filters
-- on account_id, so this index carries the whole module's read performance.
CREATE INDEX idx_jel_account ON journal_entry_lines(account_id);
CREATE INDEX idx_jel_entry ON journal_entry_lines(journal_entry_id);
