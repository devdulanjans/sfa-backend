-- Separates "how much unlocks a promotion" (min_order_qty) from "how often a free-product
-- promotion rewards after that" (repeat_every_qty; NULL = reward once). Existing buy-X-get-Y
-- promotions keep repeating every X.
ALTER TABLE promotions ADD COLUMN repeat_every_qty INTEGER;
ALTER TABLE promotions ADD COLUMN max_free_total INTEGER;

UPDATE promotions SET repeat_every_qty = min_order_qty
 WHERE type = 'FREE_PRODUCT' AND min_order_qty > 1;
