CREATE SEQUENCE IF NOT EXISTS vendor_bill_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE vendors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_code         VARCHAR(30) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    contact_person      VARCHAR(150),
    phone               VARCHAR(20),
    email               VARCHAR(120),
    address             TEXT,
    tax_number          VARCHAR(50),
    payment_terms_days  INT NOT NULL DEFAULT 30,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE vendor_bills (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_number        VARCHAR(20) NOT NULL UNIQUE,
    vendor_id          UUID NOT NULL REFERENCES vendors(id),
    bill_date          DATE NOT NULL,
    due_date           DATE NOT NULL,
    total              NUMERIC(14,2) NOT NULL,
    expense_account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    paid_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    description        TEXT,
    journal_entry_id   UUID REFERENCES journal_entries(id),
    created_by         UUID REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vendor_bills_vendor ON vendor_bills(vendor_id);

CREATE TABLE vendor_bill_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_bill_id   UUID NOT NULL REFERENCES vendor_bills(id),
    bank_account_id  UUID NOT NULL REFERENCES bank_accounts(id),
    amount           NUMERIC(14,2) NOT NULL,
    payment_date     DATE NOT NULL,
    payment_method   VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vendor_bill_payments_bill ON vendor_bill_payments(vendor_bill_id);
