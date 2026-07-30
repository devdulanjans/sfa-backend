-- Head-office/branch hierarchy: a branch is a full Customer row pointing at its parent,
-- so every existing feature that already keys off customer_id (orders, invoices, AR,
-- credit, visits, rep assignment) works for branches with no other changes. Single-level
-- only — enforced in CustomerService, not the DB — so a branch can never itself be a parent.
ALTER TABLE customers ADD COLUMN parent_customer_id UUID REFERENCES customers(id);
CREATE INDEX idx_customers_parent ON customers(parent_customer_id);
