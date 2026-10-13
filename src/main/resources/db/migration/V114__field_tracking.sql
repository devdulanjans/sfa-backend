-- Field tracking: the sales-rep mobile app records the rep's GPS position continuously while
-- they're logged in (foreground and background) and uploads it in batches. The server turns the
-- points into geofence VISITS — the rep stayed within the geofence radius of a customer for at
-- least the minimum dwell time — for the admin Field Tracking page.

CREATE TABLE location_points (
    id            BIGSERIAL     PRIMARY KEY,
    sales_rep_id  UUID          NOT NULL REFERENCES users(id),
    tenant_id     UUID          REFERENCES tenants(id),
    recorded_at   TIMESTAMPTZ   NOT NULL,
    latitude      NUMERIC(10,7) NOT NULL,
    longitude     NUMERIC(10,7) NOT NULL,
    accuracy_m    REAL,
    speed_mps     REAL,
    heading       REAL,
    battery_pct   SMALLINT,
    is_mock       BOOLEAN       NOT NULL DEFAULT FALSE,
    received_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- Re-uploading the same batch (offline retry) must not duplicate points.
    CONSTRAINT uk_location_point_rep_time UNIQUE (sales_rep_id, recorded_at)
);
CREATE INDEX idx_location_points_rep_time ON location_points(sales_rep_id, recorded_at);

CREATE TABLE geofence_visits (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_rep_id     UUID          NOT NULL REFERENCES users(id),
    customer_id      UUID          NOT NULL REFERENCES customers(id),
    tenant_id        UUID          REFERENCES tenants(id),
    arrived_at       TIMESTAMPTZ   NOT NULL,
    departed_at      TIMESTAMPTZ   NOT NULL,
    -- OPEN while the rep is (as far as we know) still inside the geofence; CLOSED once they left.
    status           VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    min_distance_m   REAL,
    point_count      INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_geofence_visits_rep_time ON geofence_visits(sales_rep_id, arrived_at);
CREATE INDEX idx_geofence_visits_customer ON geofence_visits(customer_id);

INSERT INTO system_settings (key, value, description) VALUES
('location_tracking_enabled',        'true', 'When true, the sales-rep mobile app records the rep''s location continuously while they are logged in (foreground and background) for the admin Field Tracking page.'),
('location_tracking_interval_seconds','30',  'How often (seconds) the mobile app records a location point while tracking.'),
('location_tracking_distance_meters', '25',  'Minimum movement (meters) between recorded location points.'),
('geofence_radius_meters',            '100', 'A rep within this distance (meters) of a customer''s saved location is inside that customer''s geofence.'),
('geofence_min_dwell_minutes',        '2',   'A rep must stay inside a customer''s geofence at least this many minutes for it to count as a visit.')
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_TRACKING', 'Field Tracking', '/tracking', '🛰️', NULL, 15)
ON CONFLICT (code) DO NOTHING;
