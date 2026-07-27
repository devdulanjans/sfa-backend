CREATE TABLE customer_groups (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE customer_group_members (
    customer_group_id UUID NOT NULL REFERENCES customer_groups(id) ON DELETE CASCADE,
    customer_id       UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    added_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_group_id, customer_id)
);
CREATE INDEX idx_cgm_customer ON customer_group_members(customer_id);
