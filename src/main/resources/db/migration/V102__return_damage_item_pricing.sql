-- Records the unit price (and how it was resolved) charged per return/damage line —
-- previously no price was captured at all. Existing rows default to 0 since their
-- original price was never recorded; only new items get a real resolved price.
ALTER TABLE return_items
    ADD COLUMN unit_price   NUMERIC(15,5) NOT NULL DEFAULT 0,
    ADD COLUMN price_source VARCHAR(20);

ALTER TABLE damage_items
    ADD COLUMN unit_price   NUMERIC(15,5) NOT NULL DEFAULT 0,
    ADD COLUMN price_source VARCHAR(20);
