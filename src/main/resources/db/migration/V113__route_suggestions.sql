-- Route suggestions: a lead a sales rep captures while working an assigned route that day is
-- tagged with that route; when the lead becomes a customer, the customer carries the suggestion.
-- Admins see it (Leads, Customers, the route editor) and can accept it — the customer is added as
-- the route's next stop — or dismiss it. Adding the customer to that route clears it.
ALTER TABLE leads
    ADD COLUMN suggested_route_id UUID REFERENCES sales_routes(id) ON DELETE SET NULL,
    ADD COLUMN suggested_by       UUID REFERENCES users(id),
    ADD COLUMN suggested_at       TIMESTAMPTZ;

ALTER TABLE customers
    ADD COLUMN suggested_route_id UUID REFERENCES sales_routes(id) ON DELETE SET NULL,
    ADD COLUMN suggested_by       UUID REFERENCES users(id),
    ADD COLUMN suggested_at       TIMESTAMPTZ;

CREATE INDEX idx_customers_suggested_route ON customers(suggested_route_id) WHERE suggested_route_id IS NOT NULL;
CREATE INDEX idx_leads_suggested_route     ON leads(suggested_route_id)     WHERE suggested_route_id IS NOT NULL;
