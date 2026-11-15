-- Payments a sales rep collects at the customer (V122). A collection is always accepted — the
-- money is already in the rep's hand — and settles the customer's unpaid invoices oldest due
-- first (as invoice_payments rows sharing its receipt number). Any part above what the customer
-- owed by the time it reached the server is held as a customer advance (2150) and flagged.
-- client_ref makes a retried upload from the app idempotent.
CREATE TABLE payment_collections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL REFERENCES tenants(id),
    customer_id       UUID          NOT NULL REFERENCES customers(id),
    collected_by      UUID          NOT NULL REFERENCES users(id),
    amount            NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    applied_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance_before    NUMERIC(14,2) NOT NULL DEFAULT 0,
    payment_date      DATE          NOT NULL,
    payment_method    VARCHAR(20)   NOT NULL,
    reference_number  VARCHAR(100),
    notes             VARCHAR(500),
    receipt_number    VARCHAR(30)   NOT NULL,
    client_ref        VARCHAR(64)   UNIQUE,
    latitude          NUMERIC(10,7),
    longitude         NUMERIC(10,7),
    collected_at      TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID REFERENCES users(id)
);
CREATE INDEX idx_payment_collections_tenant_date ON payment_collections (tenant_id, payment_date DESC);
CREATE INDEX idx_payment_collections_customer ON payment_collections (customer_id);

ALTER TABLE invoice_payments ADD COLUMN collection_id UUID REFERENCES payment_collections(id);
CREATE INDEX idx_invoice_payments_collection ON invoice_payments (collection_id);

INSERT INTO chart_of_accounts (account_code, account_name, account_type, is_system_account)
VALUES ('2150', 'Customer Advances', 'LIABILITY', TRUE)
ON CONFLICT DO NOTHING;

-- In-app alerts for a channel's owners/managers (bell in the admin panel). Read state per user.
CREATE TABLE admin_notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    message     VARCHAR(1000),
    link        VARCHAR(300),
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_notifications_tenant ON admin_notifications (tenant_id, created_at DESC);

CREATE TABLE admin_notification_reads (
    notification_id UUID NOT NULL REFERENCES admin_notifications(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, user_id)
);
