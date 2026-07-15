ALTER TABLE product_categories ADD COLUMN IF NOT EXISTS code VARCHAR(10);

-- Seed/upsert the two categories the invoice-number scheme depends on
-- (InvoiceService.resolveInvoiceCode: category code + first letter of customer location).
INSERT INTO product_categories (id, name, description, code)
VALUES (uuid_generate_v4(), 'Iceman Products', 'Iceman-branded products', 'IT')
ON CONFLICT (name) DO UPDATE SET code = EXCLUDED.code;

INSERT INTO product_categories (id, name, description, code)
VALUES (uuid_generate_v4(), 'Yara Product', 'Yara-branded products', 'YA')
ON CONFLICT (name) DO UPDATE SET code = EXCLUDED.code;
