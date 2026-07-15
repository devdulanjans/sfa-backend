-- Credit tracking for POS sales: amount actually paid vs outstanding balance
ALTER TABLE pos_sales
    ADD COLUMN amount_paid   DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN balance_due   DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN credit_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLICABLE';

ALTER TABLE pos_sales
    ADD CONSTRAINT chk_pos_sales_amount_paid_nonneg CHECK (amount_paid >= 0),
    ADD CONSTRAINT chk_pos_sales_balance_nonneg      CHECK (balance_due >= 0),
    ADD CONSTRAINT chk_pos_sales_paid_not_over_total CHECK (amount_paid <= total);

-- CASH/CARD sales always settle in full at time of sale
UPDATE pos_sales SET amount_paid = total, balance_due = 0, credit_status = 'NOT_APPLICABLE'
WHERE payment_method IN ('CASH', 'CARD');

-- Pre-existing CREDIT sales had no payment tracking before this feature: treat as fully unpaid
UPDATE pos_sales SET amount_paid = 0, balance_due = total,
    credit_status = CASE WHEN total = 0 THEN 'PAID' ELSE 'UNPAID' END
WHERE payment_method = 'CREDIT';

-- One-time backfill of the (previously dead) customer.current_balance from open credit sales
UPDATE customers c SET current_balance = sub.total_due
FROM (
    SELECT customer_id, SUM(balance_due) AS total_due
    FROM pos_sales
    WHERE payment_method = 'CREDIT' AND status = 'COMPLETED' AND credit_status <> 'PAID'
    GROUP BY customer_id
) sub
WHERE c.id = sub.customer_id;

CREATE INDEX idx_pos_sales_credit_status  ON pos_sales(credit_status);
CREATE INDEX idx_pos_sales_customer_credit ON pos_sales(customer_id, credit_status);
