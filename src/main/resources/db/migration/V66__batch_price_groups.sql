ALTER TABLE batch_prices ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE batch_prices ADD COLUMN customer_group_id UUID REFERENCES customer_groups(id);
ALTER TABLE batch_prices ADD COLUMN product_group_id  UUID REFERENCES product_groups(id);

-- Exactly one of (product, product group) must target this rule
ALTER TABLE batch_prices ADD CONSTRAINT chk_bp_product_xor_group
    CHECK ((product_id IS NOT NULL) != (product_group_id IS NOT NULL));

-- At most one of (customer, customer group) — both null means "applies to all customers"
ALTER TABLE batch_prices ADD CONSTRAINT chk_bp_customer_or_group_not_both
    CHECK (NOT (customer_id IS NOT NULL AND customer_group_id IS NOT NULL));

CREATE INDEX idx_bp_customer_group ON batch_prices(customer_group_id);
CREATE INDEX idx_bp_product_group  ON batch_prices(product_group_id);
