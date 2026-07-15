ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivery_address_label  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delivery_address_line   TEXT,
    ADD COLUMN IF NOT EXISTS customer_signature      TEXT,
    ADD COLUMN IF NOT EXISTS salesperson_signature   TEXT;
