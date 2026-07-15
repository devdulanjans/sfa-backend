-- Add distributor context to orders so dashboard can filter per-distributor.
-- Nullable: historical orders that predate this column show under "All Distributors" only.
ALTER TABLE orders
    ADD COLUMN distributor_id UUID REFERENCES distributors(id);

CREATE INDEX idx_orders_distributor ON orders(distributor_id);
