-- Sales routes: a named, ordered list of customer stops within a channel. An admin assigns a
-- route to a sales rep for a specific day (one route per rep per day); on that day the rep's
-- mobile app shows the route and lists only its customers. A customer may sit on several
-- routes. With no assignment for the day the rep sees their normal customer list.
CREATE TABLE sales_routes (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL REFERENCES tenants(id),
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(500),
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by   UUID          REFERENCES users(id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uk_sales_routes_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_sales_routes_tenant ON sales_routes(tenant_id);

-- Ordered stops. stop_order is the 0-based visit sequence (JPA @OrderColumn). No unique
-- (route_id, customer_id): reordering rewrites positions one row at a time, so two customers
-- swapping places would transiently collide — SalesRouteService de-duplicates instead.
CREATE TABLE sales_route_customers (
    route_id     UUID          NOT NULL REFERENCES sales_routes(id) ON DELETE CASCADE,
    customer_id  UUID          NOT NULL REFERENCES customers(id),
    stop_order   INTEGER       NOT NULL,
    PRIMARY KEY (route_id, stop_order)
);
CREATE INDEX idx_sales_route_customers_customer ON sales_route_customers(customer_id);

CREATE TABLE sales_route_assignments (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants(id),
    route_id         UUID          NOT NULL REFERENCES sales_routes(id) ON DELETE CASCADE,
    sales_rep_id     UUID          NOT NULL REFERENCES users(id),
    assignment_date  DATE          NOT NULL,
    notes            VARCHAR(500),
    assigned_by      UUID          REFERENCES users(id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT uk_route_assignment_rep_date UNIQUE (sales_rep_id, assignment_date)
);
CREATE INDEX idx_route_assignments_tenant ON sales_route_assignments(tenant_id);
CREATE INDEX idx_route_assignments_date   ON sales_route_assignments(assignment_date);
CREATE INDEX idx_route_assignments_route  ON sales_route_assignments(route_id);

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_ROUTES',        'Routes',       '/routes',        '🗺️', NULL, 12),
('MOD_ROUTE_ASSIGN',  'Assign Route', '/routes/assign', '📍', NULL, 13)
ON CONFLICT (code) DO NOTHING;
