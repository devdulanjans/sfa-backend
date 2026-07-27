-- Daily start/end vehicle mileage per sales rep. One row per user per calendar day,
-- enforced at the DB level (mirrors idx_drawer_sessions_one_open's role for cash drawer).
CREATE TABLE mileage_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id),
    log_date       DATE NOT NULL,
    start_mileage  DECIMAL(10,1) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_mileage    DECIMAL(10,1),
    ended_at       TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, log_date)
);

CREATE INDEX idx_mileage_logs_user_date ON mileage_logs(user_id, log_date);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_RPT_MILEAGE', 'Mileage', '/reports/mileage', '🛣️', 'MOD_REPORTS', 30)
ON CONFLICT (code) DO NOTHING;
