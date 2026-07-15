-- Splits Return from a single-product-per-row model into a header/detail model
-- so one return can cover multiple products in a single submission.
CREATE TABLE return_items (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    return_id   UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id),
    quantity    NUMERIC(10,3) NOT NULL
);

CREATE INDEX idx_return_items_return ON return_items(return_id);

-- Backfill: each existing return row becomes its own single-line return_items row.
INSERT INTO return_items (id, return_id, product_id, quantity)
SELECT uuid_generate_v4(), id, product_id, quantity FROM returns;

ALTER TABLE returns DROP COLUMN product_id;
ALTER TABLE returns DROP COLUMN quantity;
