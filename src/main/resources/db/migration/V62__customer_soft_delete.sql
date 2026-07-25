ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_customers_deleted_at ON customers(deleted_at);
