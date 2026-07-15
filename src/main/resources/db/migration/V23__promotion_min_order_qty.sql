-- Add minimum order quantity threshold to FREE_PRODUCT promotions.
-- Free product is only auto-added to the cart once the total qty of applicable
-- products in the cart reaches this threshold.  Default 1 = current behaviour.
ALTER TABLE promotions
    ADD COLUMN min_order_qty INTEGER NOT NULL DEFAULT 1;
