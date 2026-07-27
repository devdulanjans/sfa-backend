-- Mileage was originally modeled as one row per user per calendar day, but real
-- usage showed a sales rep who logs out mid-day (recording end mileage) and logs
-- back in later the same day must be asked for start mileage again — i.e. mileage
-- is tracked per login/logout session, not per calendar day; several sessions can
-- legitimately happen in one day. Replace the per-day uniqueness with the
-- cash-drawer-style invariant instead: at most one OPEN (end_mileage IS NULL)
-- session per user at a time.
-- Look up the actual constraint name rather than assuming Postgres's default
-- naming, so this doesn't silently no-op if it was named differently.
DO $$
DECLARE
    con_name text;
BEGIN
    SELECT tc.constraint_name INTO con_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name
    WHERE tc.table_name = 'mileage_logs'
      AND tc.constraint_type = 'UNIQUE'
    GROUP BY tc.constraint_name
    HAVING array_agg(kcu.column_name::text ORDER BY kcu.column_name) = ARRAY['log_date', 'user_id'];

    IF con_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE mileage_logs DROP CONSTRAINT %I', con_name);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mileage_logs_one_open ON mileage_logs(user_id) WHERE end_mileage IS NULL;
