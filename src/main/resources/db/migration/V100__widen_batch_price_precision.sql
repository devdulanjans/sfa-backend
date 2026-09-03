-- Batch pricing needs 5 decimal places (very low per-unit rates round away to 0.00 at
-- 2dp). Widening the column is safe on existing data — NUMERIC(12,2)/(12,2) values already
-- fit within NUMERIC(15,5) unchanged, this only allows more precision going forward.
ALTER TABLE batch_prices ALTER COLUMN price TYPE NUMERIC(15,5);

-- order_items.unit_price must carry the same precision, otherwise a 5-decimal batch price
-- is silently rounded back to 2dp the moment an order is placed from it.
ALTER TABLE order_items ALTER COLUMN unit_price TYPE NUMERIC(15,5);
