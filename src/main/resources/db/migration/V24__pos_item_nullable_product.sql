-- Allow POS sale items without a catalog product ID (free-text / quick-entry items).
ALTER TABLE pos_sale_items
    ALTER COLUMN product_id DROP NOT NULL;
