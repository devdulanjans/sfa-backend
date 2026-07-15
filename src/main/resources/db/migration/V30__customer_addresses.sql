CREATE TABLE customer_addresses (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    label        VARCHAR(100) NOT NULL,
    address_line TEXT         NOT NULL,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_cust_addr_customer_id ON customer_addresses (customer_id);

-- Migrate existing single-address values into the new table
INSERT INTO customer_addresses (customer_id, label, address_line, is_primary, sort_order)
SELECT id, 'Registered', address, TRUE, 0
FROM customers
WHERE address IS NOT NULL AND TRIM(address) != '';

ALTER TABLE customers DROP COLUMN IF EXISTS address;
