-- Payments made to the tax authority against the Tax Payable balance that already
-- accrues automatically from invoice-issued postings (see V77's system accounts and
-- InvoiceService.postInvoiceIssuedEntry). Dr Tax Payable / Cr Bank on each payment.
CREATE TABLE tax_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id  UUID NOT NULL REFERENCES bank_accounts(id),
    amount           NUMERIC(14,2) NOT NULL,
    payment_date     DATE NOT NULL,
    reference_number VARCHAR(100),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
