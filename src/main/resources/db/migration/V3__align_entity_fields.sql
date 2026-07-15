-- Align database columns with updated entity definitions

-- customers: rename code → customer_code, vat_number → tax_number, add missing columns
ALTER TABLE customers
    RENAME COLUMN code TO customer_code;

ALTER TABLE customers
    RENAME COLUMN vat_number TO tax_number;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS contact_person  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS email           VARCHAR(120),
    ADD COLUMN IF NOT EXISTS credit_limit    NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS current_balance NUMERIC(12,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS latitude        NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS longitude       NUMERIC(10,7);

-- products: rename code → product_code
ALTER TABLE products
    RENAME COLUMN code TO product_code;

-- batch_prices: rename effective_date/expiry_date → start_date/end_date, add min_qty
ALTER TABLE batch_prices
    RENAME COLUMN effective_date TO start_date;

ALTER TABLE batch_prices
    RENAME COLUMN expiry_date TO end_date;

ALTER TABLE batch_prices
    ADD COLUMN IF NOT EXISTS min_qty NUMERIC(10,3);

-- promotions: rename value → discount_value, active → is_active
ALTER TABLE promotions
    RENAME COLUMN value TO discount_value;

ALTER TABLE promotions
    RENAME COLUMN active TO is_active;

-- customer_visits: add checkout lat/lng columns
ALTER TABLE customer_visits
    ADD COLUMN IF NOT EXISTS checkout_latitude  NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS checkout_longitude NUMERIC(10,7);

-- returns: add sales_rep_id FK and updated_at
ALTER TABLE returns
    ADD COLUMN IF NOT EXISTS sales_rep_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ;

-- damages: add reported_by_id FK, rename remarks → description
ALTER TABLE damages
    ADD COLUMN IF NOT EXISTS reported_by_id UUID REFERENCES users(id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='damages' AND column_name='remarks') THEN
        ALTER TABLE damages RENAME COLUMN remarks TO description;
    END IF;
END $$;

-- Re-create affected indexes with new column names
DROP INDEX IF EXISTS idx_customers_code;
CREATE INDEX idx_customers_code ON customers(customer_code);

DROP INDEX IF EXISTS idx_products_code;
CREATE INDEX idx_products_code ON products(product_code);

DROP INDEX IF EXISTS idx_bp_product_date;
CREATE INDEX idx_bp_product_date ON batch_prices(product_id, start_date);

DROP INDEX IF EXISTS idx_promo_active_date;
CREATE INDEX idx_promo_active_date ON promotions(is_active, start_date, end_date);
