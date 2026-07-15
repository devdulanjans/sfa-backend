-- Replace AtomicLong-based invoice number generation with a DB sequence
-- so numbers are unique and survive server restarts.
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO CYCLE;

-- Sync past any already-issued invoices so the next value is always fresh.
DO $$
DECLARE
    max_seq BIGINT;
BEGIN
    SELECT COALESCE(
        MAX(CAST(NULLIF(SPLIT_PART(invoice_number, '_BR07_', 2), '') AS BIGINT)), 0)
    INTO max_seq
    FROM invoices
    WHERE invoice_number LIKE '%_BR07_%';

    IF max_seq > 0 THEN
        PERFORM setval('invoice_number_seq', max_seq);
    END IF;
END $$;
