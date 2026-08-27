-- Business/company registration number (Registrar of Companies), distinct from the
-- existing tax_id (TIN) and vat_registration_number fields.
ALTER TABLE company_profile ADD COLUMN registration_number VARCHAR(100);
