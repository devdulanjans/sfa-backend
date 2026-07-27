INSERT INTO system_settings (key, value, description) VALUES
('sales_target_enabled', 'false', 'When true, monthly product sales targets (with automatic daily working-day breakdown) are enabled for sales reps. When false, the feature is hidden everywhere.'),
('working_days', 'MON,TUE,WED,THU,FRI,SAT', 'Comma-separated weekdays (MON..SUN) considered working days for sales target daily-breakdown calculations.')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE target_holidays (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL UNIQUE,
    description  VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE monthly_sales_targets (
    id           UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_rep_id UUID NOT NULL REFERENCES users(id),
    product_id   UUID NOT NULL REFERENCES products(id),
    target_year  INTEGER NOT NULL,
    target_month INTEGER NOT NULL,
    target_qty   DECIMAL(12,2) NOT NULL,
    created_by   UUID REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sales_rep_id, product_id, target_year, target_month)
);
CREATE INDEX idx_monthly_sales_targets_rep ON monthly_sales_targets(sales_rep_id, target_year, target_month);

-- New module group, same pattern as MOD_REPORTS in V20 (a real header row, url=NULL)
INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_SALES_TARGET',    'Sales Targets',    NULL,                       '🎯', NULL,               110),
('MOD_SALES_TARGETS',   'Targets',          '/sales-targets',           '🎯', 'MOD_SALES_TARGET', 10),
('MOD_TARGET_CALENDAR', 'Working Calendar', '/sales-targets/calendar',  '📅', 'MOD_SALES_TARGET', 20)
ON CONFLICT (code) DO NOTHING;
