-- Safe backfill: no invoice is ever PAID today (InvoiceStatus.PAID was declared but
-- never assigned before this module existed), so every existing row correctly starts at 0.
ALTER TABLE invoices ADD COLUMN paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

CREATE TABLE invoice_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoices(id),
    bank_account_id  UUID NOT NULL REFERENCES bank_accounts(id),
    amount           NUMERIC(14,2) NOT NULL,
    payment_date     DATE NOT NULL,
    payment_method   VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_payments_invoice ON invoice_payments(invoice_id);
