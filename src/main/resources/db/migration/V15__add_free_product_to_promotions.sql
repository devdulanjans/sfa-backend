-- FREE_PRODUCT promotion type: stores which product is given for free
ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS free_product_id UUID REFERENCES products(id);
