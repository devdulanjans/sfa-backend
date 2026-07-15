-- ============================================================
-- Sales Rep Customer Assignment
-- Flyway Migration V10
-- Empty set = access ALL customers; non-empty = access only listed customers
-- ============================================================

CREATE TABLE user_customers (
    user_id      UUID NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    customer_id  UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, customer_id)
);

CREATE INDEX idx_uc_user     ON user_customers(user_id);
CREATE INDEX idx_uc_customer ON user_customers(customer_id);
