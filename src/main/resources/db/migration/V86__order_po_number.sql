-- Optional purchase-order number entered by the sales rep on the Order Review
-- screen — prints on the generated invoice's "Purchase Order No" line when present.
ALTER TABLE orders ADD COLUMN po_number VARCHAR(50);
