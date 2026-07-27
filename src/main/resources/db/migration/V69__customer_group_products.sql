CREATE TABLE customer_group_products (
    customer_group_id UUID NOT NULL REFERENCES customer_groups(id) ON DELETE CASCADE,
    product_id        UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    added_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_group_id, product_id)
);
CREATE INDEX idx_cgp_product ON customer_group_products(product_id);
