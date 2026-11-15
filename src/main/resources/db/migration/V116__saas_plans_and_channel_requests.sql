-- Public SaaS sign-up: the plans a business can choose on the website, and the
-- "Create your SFA" requests the super admin reviews and approves into a channel.

CREATE TABLE subscription_plans (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Stable public id used by the website (?plan=standard) and in sign-up requests.
    code           VARCHAR(30)   NOT NULL UNIQUE,
    name           VARCHAR(100)  NOT NULL,
    description    VARCHAR(500),
    price_per_user NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'LKR',
    max_owners     INTEGER       NOT NULL DEFAULT 1,
    -- Users besides the owner(s); NULL = unlimited.
    max_users      INTEGER,
    features       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    highlighted    BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order     INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

INSERT INTO subscription_plans (code, name, description, price_per_user, max_owners, max_users, features, highlighted, sort_order)
VALUES
  ('free', 'Free', 'Try AVLIS with your own products and one sales rep.', 0, 1, 1,
   '["1 owner login", "1 sales rep login", "Orders & invoicing", "Products, prices & promotions"]'::jsonb,
   FALSE, 10),
  ('standard', 'Standard', 'Everything your team needs to sell, deliver and get paid.', 3000, 1, NULL,
   '["Users as you need them", "Routes, visits & live tracking", "Customer self-ordering app", "Your own invoice layout", "Finance & reports"]'::jsonb,
   TRUE, 20);

CREATE SEQUENCE channel_request_seq START 1;

CREATE TABLE channel_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_no    VARCHAR(30)  NOT NULL UNIQUE,
    business_name   VARCHAR(150) NOT NULL,
    business_type   VARCHAR(60)  NOT NULL,
    business_reg_no VARCHAR(50),
    address         VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    owner_name      VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    plan_id         UUID         NOT NULL REFERENCES subscription_plans(id),
    user_count      INTEGER      NOT NULL,
    message         VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    review_note     VARCHAR(1000),
    reviewed_by     UUID REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    -- Set on approval: the channel and owner login created from this request.
    tenant_id       UUID REFERENCES tenants(id),
    owner_user_id   UUID REFERENCES users(id),
    source_ip       VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT chk_channel_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_channel_requests_status ON channel_requests(status, created_at DESC);
CREATE INDEX idx_channel_requests_email  ON channel_requests(lower(email));
