-- Stock levels: current on-hand per product (single global pool)
CREATE TABLE stock_levels (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID            NOT NULL UNIQUE REFERENCES products(id),
    on_hand     DECIMAL(12,3)   NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved    DECIMAL(12,3)   NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_levels_product ON stock_levels(product_id);

-- Stock movements: immutable audit trail of every stock change
CREATE TABLE stock_movements (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID            NOT NULL REFERENCES products(id),
    type            VARCHAR(30)     NOT NULL,
    quantity        DECIMAL(12,3)   NOT NULL,
    balance_after   DECIMAL(12,3)   NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    UUID,
    notes           TEXT,
    created_by      UUID            REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_product ON stock_movements(product_id, created_at DESC);
CREATE INDEX idx_stock_movements_ref     ON stock_movements(reference_type, reference_id);

-- Register inventory_tracking system setting (disabled by default)
INSERT INTO system_settings(key, value, description, updated_at)
VALUES ('inventory_tracking', 'false', 'Enable inventory stock tracking and deduction on order approval', NOW())
ON CONFLICT (key) DO NOTHING;
