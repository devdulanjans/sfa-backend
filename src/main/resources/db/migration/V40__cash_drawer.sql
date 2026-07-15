-- Cash drawer sessions: opening float, running expected cash, day-end close & variance
CREATE TABLE drawer_sessions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cashier_id     UUID NOT NULL REFERENCES users(id),
    opening_float  DECIMAL(12,2) NOT NULL,
    opened_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at      TIMESTAMPTZ,
    expected_cash  DECIMAL(12,2),
    counted_cash   DECIMAL(12,2),
    variance       DECIMAL(12,2),
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opening_notes  TEXT,
    closing_notes  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drawer_sessions_cashier ON drawer_sessions(cashier_id, status);

-- Guarantee at most one OPEN session per cashier at the DB level too
CREATE UNIQUE INDEX idx_drawer_sessions_one_open ON drawer_sessions(cashier_id) WHERE status = 'OPEN';

-- Manual cash movements (deposits / withdrawals) recorded during a session
CREATE TABLE cash_movements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES drawer_sessions(id),
    type        VARCHAR(20) NOT NULL,
    amount      DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    notes       TEXT,
    recorded_by UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cash_movements_session ON cash_movements(session_id, created_at);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_POS_DRAWER', 'Cash Drawer', '/pos/drawer', '💵', 'MOD_POS', 40)
ON CONFLICT (code) DO NOTHING;
