-- Extend customer_code from VARCHAR(20) to VARCHAR(30) to match entity definition
ALTER TABLE customers
    ALTER COLUMN customer_code TYPE VARCHAR(30);
