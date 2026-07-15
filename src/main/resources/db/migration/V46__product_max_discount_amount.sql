-- Convert existing percentage caps into an equivalent fixed Rs amount before renaming,
-- so a product priced at 100 with a 20% cap keeps behaving as a ~20.00 cap afterward.
UPDATE products
SET max_discount_pct = ROUND(default_price * max_discount_pct / 100, 2)
WHERE max_discount_pct IS NOT NULL;

ALTER TABLE products RENAME COLUMN max_discount_pct TO max_discount_amount;
ALTER TABLE products ALTER COLUMN max_discount_amount TYPE NUMERIC(12,2);
