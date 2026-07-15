-- Many-to-many: one promotion can cover multiple products
CREATE TABLE promotion_products (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    product_id   UUID NOT NULL REFERENCES products(id)  ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, product_id)
);

CREATE INDEX idx_promo_products_product ON promotion_products (product_id);

-- Migrate existing single-product rows into the join table
INSERT INTO promotion_products (promotion_id, product_id)
SELECT id, product_id
FROM   promotions
WHERE  product_id IS NOT NULL;

-- Drop the old single-product column + its index
DROP INDEX IF EXISTS idx_promo_product;
ALTER TABLE promotions DROP COLUMN IF EXISTS product_id;
