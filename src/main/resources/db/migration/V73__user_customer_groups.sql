-- Lets a user (typically SALES_REP) be assigned whole Customer Groups, in addition
-- to (or instead of) individually-assigned customers — the effective customer scope
-- used for visibility restriction is direct assignedCustomers UNION every group's
-- members (computed in UserDetailsImpl, mirroring how Customer's
-- effectiveAssignedProductIds unions direct + group product assignments).
CREATE TABLE user_customer_groups (
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    customer_group_id  UUID NOT NULL REFERENCES customer_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, customer_group_id)
);

CREATE INDEX idx_ucg_group ON user_customer_groups(customer_group_id);
