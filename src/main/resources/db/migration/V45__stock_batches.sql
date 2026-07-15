CREATE TABLE stock_batches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID NOT NULL REFERENCES products(id),
    received_qty   NUMERIC(12,3) NOT NULL CHECK (received_qty > 0),
    remaining_qty  NUMERIC(12,3) NOT NULL CHECK (remaining_qty >= 0),
    unit_cost      NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    received_date  DATE NOT NULL,
    notes          TEXT,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stock_batches_product_fifo ON stock_batches(product_id, received_date, created_at);

CREATE TABLE stock_batch_consumptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id     UUID NOT NULL REFERENCES stock_batches(id),
    movement_id  UUID NOT NULL REFERENCES stock_movements(id),
    quantity     NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_cost    NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_batch_consumptions_movement ON stock_batch_consumptions(movement_id);
CREATE INDEX idx_batch_consumptions_batch   ON stock_batch_consumptions(batch_id);

ALTER TABLE stock_movements ADD COLUMN total_cost NUMERIC(12,2);
