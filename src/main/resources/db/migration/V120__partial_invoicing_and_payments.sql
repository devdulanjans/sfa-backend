-- Partial invoicing and balance payments.
--
-- An approved order no longer has to be invoiced in one go: each invoice covers chosen
-- quantities of the order's lines (invoice_items), the order line keeps a running
-- invoiced_qty, and whatever the owner decides will never be supplied is closed_qty.
-- Payments can be partial (already supported) and no longer need a bank account: without
-- one the money is held on "Cash on Hand" (1050) until deposited.

-- ── Order lines: how much is invoiced / closed ───────────────────────────────────────
ALTER TABLE order_items ADD COLUMN invoiced_qty NUMERIC(10,3) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN closed_qty   NUMERIC(10,3) NOT NULL DEFAULT 0;

-- PARTIALLY_INVOICED is 18 characters.
ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(20);

-- ── Several invoices per order ────────────────────────────────────────────────────────
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_order_id_key;
CREATE INDEX IF NOT EXISTS idx_invoices_order ON invoices(order_id);

ALTER TABLE invoices ADD COLUMN notes         TEXT;
ALTER TABLE invoices ADD COLUMN cancelled_at  TIMESTAMPTZ;
ALTER TABLE invoices ADD COLUMN cancelled_by  UUID REFERENCES users(id);
ALTER TABLE invoices ADD COLUMN cancel_reason VARCHAR(255);

CREATE TABLE invoice_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    order_item_id   UUID NOT NULL REFERENCES order_items(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        NUMERIC(10,3) NOT NULL,
    unit_price      NUMERIC(15,5) NOT NULL,
    discount_pct    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    tax_pct         NUMERIC(5,2)  NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    line_total      NUMERIC(14,2) NOT NULL DEFAULT 0,
    price_source    VARCHAR(20),
    promotion_name  VARCHAR(150),
    line_no         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_invoice_items_qty CHECK (quantity > 0)
);
CREATE INDEX idx_invoice_items_invoice    ON invoice_items(invoice_id);
CREATE INDEX idx_invoice_items_order_item ON invoice_items(order_item_id);

-- Every existing invoice covered its whole order: copy the order's lines onto it and mark
-- them fully invoiced (cancelled invoices, if any, release nothing).
INSERT INTO invoice_items (invoice_id, order_item_id, product_id, quantity, unit_price, discount_pct, tax_pct,
                           discount_amount, tax_amount, line_total, price_source, promotion_name, line_no)
SELECT i.id, oi.id, oi.product_id, oi.quantity, oi.unit_price, oi.discount_pct, oi.tax_pct,
       oi.discount_amount, oi.tax_amount, oi.line_total, oi.price_source, oi.promotion_name, oi.line_no
FROM invoices i
JOIN order_items oi ON oi.order_id = i.order_id
WHERE oi.quantity > 0;

UPDATE order_items oi SET invoiced_qty = oi.quantity
WHERE EXISTS (SELECT 1 FROM invoices i WHERE i.order_id = oi.order_id AND i.status <> 'CANCELLED');

-- ── Payments ─────────────────────────────────────────────────────────────────────────
ALTER TABLE invoice_payments ALTER COLUMN bank_account_id DROP NOT NULL;
ALTER TABLE invoice_payments ADD COLUMN notes        TEXT;
ALTER TABLE invoice_payments ADD COLUMN voided_at    TIMESTAMPTZ;
ALTER TABLE invoice_payments ADD COLUMN voided_by    UUID REFERENCES users(id);
ALTER TABLE invoice_payments ADD COLUMN void_reason  VARCHAR(255);
-- One customer payment settled across several invoices shares a receipt number.
ALTER TABLE invoice_payments ADD COLUMN receipt_number VARCHAR(30);
CREATE INDEX idx_invoice_payments_receipt ON invoice_payments(receipt_number);

CREATE SEQUENCE IF NOT EXISTS payment_receipt_seq START 1;

-- Money received but not (yet) banked — the debit side of a payment with no bank account.
INSERT INTO chart_of_accounts (account_code, account_name, account_type, is_system_account)
VALUES ('1050', 'Cash on Hand', 'ASSET', TRUE)
ON CONFLICT (account_code) DO NOTHING;
