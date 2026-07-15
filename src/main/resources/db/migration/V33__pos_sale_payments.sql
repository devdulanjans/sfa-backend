-- Ledger of payments recorded against POS credit sales (initial partial payment + later settlements)
CREATE TABLE pos_sale_payments (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id        UUID          NOT NULL REFERENCES pos_sales(id),
    customer_id    UUID          NOT NULL REFERENCES customers(id),
    amount         DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20)   NOT NULL,
    payment_type   VARCHAR(20)   NOT NULL DEFAULT 'SETTLEMENT',
    balance_after  DECIMAL(12,2) NOT NULL,
    notes          TEXT,
    recorded_by    UUID          NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pos_sale_payments_sale     ON pos_sale_payments(sale_id, created_at DESC);
CREATE INDEX idx_pos_sale_payments_customer ON pos_sale_payments(customer_id, created_at DESC);

ALTER TABLE customers ADD CONSTRAINT chk_customers_balance_nonneg CHECK (current_balance >= 0);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_CREDIT', 'Customer Credit', '/pos/credit', '💳', 'MOD_POS', 30)
ON CONFLICT (code) DO NOTHING;
