-- Track a barcode per product (optional), used for POS barcode scanning
ALTER TABLE products ADD COLUMN barcode VARCHAR(64);

-- Partial unique index: enforces uniqueness only among products that have a barcode set
CREATE UNIQUE INDEX idx_products_barcode ON products(barcode) WHERE barcode IS NOT NULL;
