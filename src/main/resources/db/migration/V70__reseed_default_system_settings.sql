-- Re-seeds default system_settings rows if missing. Needed because V9/V37/V43/V53's
-- INSERTs only ever run once per database — if this database's flyway_schema_history
-- already marks those versions as applied (e.g. it was baselined from a schema-only
-- dump) without the seed data actually landing, the rows never come back and
-- /settings/general renders with nothing to show. Same fix pattern as V63's superadmin
-- reseed. ON CONFLICT (key) DO NOTHING makes this safe to run whether or not the rows
-- already exist.
INSERT INTO system_settings (key, value, description) VALUES
('isOrderPrevent', 'false', 'When true, an invoice is automatically generated the moment an order is created, skipping the manual approval and invoice steps.'),
('pos_tax_enabled', 'true', 'When true, tax is calculated and applied to POS sales. When false, POS sales are billed with no tax.'),
('pos_auto_print_receipt', 'true', 'When true, the receipt prints automatically as soon as a POS sale completes. When false, the cashier must print manually from the sale confirmation screen.'),
('pos_print_copies_default', '1', 'Default number of receipt copies printed per POS sale (1-10). Cashiers can still adjust the copy count for an individual sale.'),
('show_promotion_as_discount', 'false', 'When true, free items from Buy-X-Get-Y promotions are added to orders/invoices as a real priced line with the value shown as a Discount, instead of a LKR 0.00 line. Applies system-wide.')
ON CONFLICT (key) DO NOTHING;
