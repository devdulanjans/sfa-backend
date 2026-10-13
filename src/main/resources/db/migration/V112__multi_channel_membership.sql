-- Multi-channel membership for customers, products, promotions and batch prices.
--
-- Until now each of these rows belonged to exactly one channel (tenant_id). Codes are already
-- globally unique and stock is kept once per product, so a customer/product can't be copied per
-- channel — instead one row can now be AVAILABLE IN several channels. tenant_id stays as the
-- row's "home" channel (the default, and where it was created); these tables list every channel
-- the row is available in, and the Hibernate tenantFilter now checks them instead of tenant_id.
-- Orders, invoices, returns and routes stay single-channel.
--
-- Backfill: every row starts available in exactly its current channel, so nothing changes until
-- an admin adds more. Rows with no channel (legacy, tenant_id NULL) stay unassigned.

CREATE TABLE customer_channels (
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id)   ON DELETE CASCADE,
    PRIMARY KEY (customer_id, tenant_id)
);
CREATE INDEX idx_customer_channels_tenant ON customer_channels(tenant_id);
INSERT INTO customer_channels (customer_id, tenant_id)
    SELECT id, tenant_id FROM customers WHERE tenant_id IS NOT NULL;

CREATE TABLE product_channels (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tenant_id  UUID NOT NULL REFERENCES tenants(id)  ON DELETE CASCADE,
    PRIMARY KEY (product_id, tenant_id)
);
CREATE INDEX idx_product_channels_tenant ON product_channels(tenant_id);
INSERT INTO product_channels (product_id, tenant_id)
    SELECT id, tenant_id FROM products WHERE tenant_id IS NOT NULL;

CREATE TABLE promotion_channels (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES tenants(id)    ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, tenant_id)
);
CREATE INDEX idx_promotion_channels_tenant ON promotion_channels(tenant_id);
INSERT INTO promotion_channels (promotion_id, tenant_id)
    SELECT id, tenant_id FROM promotions WHERE tenant_id IS NOT NULL;

CREATE TABLE batch_price_channels (
    batch_price_id UUID NOT NULL REFERENCES batch_prices(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL REFERENCES tenants(id)      ON DELETE CASCADE,
    PRIMARY KEY (batch_price_id, tenant_id)
);
CREATE INDEX idx_batch_price_channels_tenant ON batch_price_channels(tenant_id);
INSERT INTO batch_price_channels (batch_price_id, tenant_id)
    SELECT id, tenant_id FROM batch_prices WHERE tenant_id IS NOT NULL;
