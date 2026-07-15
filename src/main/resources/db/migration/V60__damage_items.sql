-- Splits Damage from a single-product-per-row model into a header/detail model
-- (mirrors V59__return_items.sql for returns), and adds an approval workflow.
CREATE TABLE damage_items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    damage_id   UUID NOT NULL REFERENCES damages(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id),
    quantity    NUMERIC(10,3) NOT NULL
);

CREATE INDEX idx_damage_items_damage ON damage_items(damage_id);

-- Backfill: each existing damage row becomes its own single-line damage_items row.
INSERT INTO damage_items (id, damage_id, product_id, quantity)
SELECT uuid_generate_v4(), id, product_id, quantity FROM damages;

ALTER TABLE damages DROP COLUMN product_id;
ALTER TABLE damages DROP COLUMN quantity;

ALTER TABLE damages ADD COLUMN status VARCHAR(15) NOT NULL DEFAULT 'PENDING';
ALTER TABLE damages ADD COLUMN updated_at TIMESTAMPTZ;
