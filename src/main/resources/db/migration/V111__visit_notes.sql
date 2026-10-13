-- Visit notes: when a sales rep visits a customer and no order is placed, the rep records why
-- (an admin-managed VISIT reason + optional note). One note per rep, customer and day — recording
-- again that day updates it, which also makes offline retries idempotent. The route/assignment
-- and distributor are captured so the admin report can filter by route, rep, channel and
-- distributor.
CREATE TABLE visit_notes (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenants(id),
    customer_id    UUID          NOT NULL REFERENCES customers(id),
    sales_rep_id   UUID          NOT NULL REFERENCES users(id),
    route_id       UUID          REFERENCES sales_routes(id) ON DELETE SET NULL,
    assignment_id  UUID          REFERENCES sales_route_assignments(id) ON DELETE SET NULL,
    distributor_id UUID          REFERENCES distributors(id),
    visit_date     DATE          NOT NULL,
    visited_at     TIMESTAMPTZ   NOT NULL,
    reason_id      UUID          REFERENCES reasons(id) ON DELETE SET NULL,
    reason_label   VARCHAR(200)  NOT NULL,
    note           TEXT,
    latitude       NUMERIC(10,7),
    longitude      NUMERIC(10,7),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uk_visit_note_rep_customer_day UNIQUE (sales_rep_id, customer_id, visit_date)
);
CREATE INDEX idx_visit_notes_tenant      ON visit_notes(tenant_id);
CREATE INDEX idx_visit_notes_date        ON visit_notes(visit_date);
CREATE INDEX idx_visit_notes_route       ON visit_notes(route_id);
CREATE INDEX idx_visit_notes_distributor ON visit_notes(distributor_id);

-- Starter "no order" reasons — editable from Settings → Reasons (Visit tab).
INSERT INTO reasons (type, label, allow_free_text, sort_order) VALUES
('VISIT', 'Shop closed',                   FALSE, 1),
('VISIT', 'Owner / buyer not available',   FALSE, 2),
('VISIT', 'Enough stock — no need now',    FALSE, 3),
('VISIT', 'No cash / budget',              FALSE, 4),
('VISIT', 'Price too high',                FALSE, 5),
('VISIT', 'Buying from a competitor',      FALSE, 6),
('VISIT', 'Other',                         TRUE,  7)
ON CONFLICT (type, label) DO NOTHING;

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_VISIT_NOTES', 'Visit Notes', '/routes/visit-notes', '📝', NULL, 14)
ON CONFLICT (code) DO NOTHING;
