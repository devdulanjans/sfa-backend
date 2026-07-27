ALTER TABLE promotions ADD COLUMN customer_group_id UUID REFERENCES customer_groups(id);
ALTER TABLE promotions ADD COLUMN product_group_id  UUID REFERENCES product_groups(id);

-- At most one of (customer, customer group) — both null means "applies to all customers"
ALTER TABLE promotions ADD CONSTRAINT chk_promo_customer_or_group_not_both
    CHECK (NOT (customer_id IS NOT NULL AND customer_group_id IS NOT NULL));

CREATE INDEX idx_promo_customer_group ON promotions(customer_group_id);
CREATE INDEX idx_promo_product_group  ON promotions(product_group_id);
