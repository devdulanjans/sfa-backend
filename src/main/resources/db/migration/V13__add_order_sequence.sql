-- Replace AtomicLong-based order number generation with a DB sequence to prevent
-- duplicate order numbers on server restart
CREATE SEQUENCE IF NOT EXISTS order_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO CYCLE;

-- Sync sequence past any already-used numbers so next value is always fresh
DO $$
DECLARE
    max_seq BIGINT;
BEGIN
    SELECT COALESCE(
        MAX(CAST(NULLIF(SPLIT_PART(order_number, '-', 3), '') AS BIGINT)), 0)
    INTO max_seq
    FROM orders
    WHERE order_number LIKE 'ORD-%';

    IF max_seq > 0 THEN
        PERFORM setval('order_number_seq', max_seq);
    END IF;
END $$;
