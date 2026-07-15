-- ============================================================
-- SFA System - Initial Database Schema
-- Flyway Migration V1
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ROLES
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    permissions JSONB,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE
);

-- USERS
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username      VARCHAR(60)  NOT NULL UNIQUE,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_id       UUID NOT NULL REFERENCES roles(id),
    full_name     VARCHAR(120),
    phone         VARCHAR(20),
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_users_role ON users(role_id);

-- CUSTOMER CATEGORIES
CREATE TABLE customer_categories (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- CUSTOMERS
CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    address         TEXT,
    phone           VARCHAR(20),
    vat_number      VARCHAR(30),
    tax_type        VARCHAR(15)  NOT NULL DEFAULT 'STANDARD',
    category_id     UUID REFERENCES customer_categories(id),
    visibility_rule VARCHAR(10)  NOT NULL DEFAULT 'ALL',
    status          VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID REFERENCES users(id)
);
CREATE INDEX idx_customers_code   ON customers(code);
CREATE INDEX idx_customers_status ON customers(status);
CREATE INDEX idx_customers_name   ON customers USING gin(name gin_trgm_ops);

-- PRODUCT CATEGORIES
CREATE TABLE product_categories (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- UNITS OF MEASURE
CREATE TABLE units (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         VARCHAR(50) NOT NULL UNIQUE,
    abbreviation VARCHAR(10)
);

-- PRODUCTS
CREATE TABLE products (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(20)    NOT NULL UNIQUE,
    name             VARCHAR(200)   NOT NULL,
    description      TEXT,
    category_id      UUID REFERENCES product_categories(id),
    unit_id          UUID REFERENCES units(id),
    default_price    NUMERIC(12,2)  NOT NULL DEFAULT 0,
    tax_rate         NUMERIC(5,2)   NOT NULL DEFAULT 15.00,
    max_discount_pct NUMERIC(5,2)   NOT NULL DEFAULT 20.00,
    status           VARCHAR(15)    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ
);
CREATE INDEX idx_products_code     ON products(code);
CREATE INDEX idx_products_status   ON products(status);
CREATE INDEX idx_products_name     ON products USING gin(name gin_trgm_ops);

-- CUSTOMER PRODUCT VISIBILITY
CREATE TABLE customer_products (
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id)  ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_id, product_id)
);

-- PROMOTIONS
CREATE TABLE promotions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(15)  NOT NULL DEFAULT 'PERCENTAGE',
    value       NUMERIC(10,2) NOT NULL,
    product_id  UUID REFERENCES products(id),
    customer_id UUID REFERENCES customers(id),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_promo_active ON promotions(active, start_date, end_date) WHERE active = TRUE;

-- BATCH PRICES
CREATE TABLE batch_prices (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id     UUID NOT NULL REFERENCES products(id),
    customer_id    UUID REFERENCES customers(id),
    promotion_id   UUID REFERENCES promotions(id),
    price          NUMERIC(12,2) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date    DATE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID REFERENCES users(id)
);
CREATE INDEX idx_bp_product_date ON batch_prices(product_id, effective_date DESC);
CREATE INDEX idx_bp_customer     ON batch_prices(customer_id) WHERE customer_id IS NOT NULL;

-- ORDERS
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number    VARCHAR(20)   NOT NULL UNIQUE,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    sales_rep_id    UUID NOT NULL REFERENCES users(id),
    approved_by     UUID REFERENCES users(id),
    status          VARCHAR(15)   NOT NULL DEFAULT 'DRAFT',
    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    order_date      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_orders_customer   ON orders(customer_id);
CREATE INDEX idx_orders_rep_status ON orders(sales_rep_id, status);
CREATE INDEX idx_orders_date       ON orders(order_date);

-- ORDER ITEMS
CREATE TABLE order_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        NUMERIC(10,3) NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL,
    discount_pct    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    tax_pct         NUMERIC(5,2)  NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    line_total      NUMERIC(14,2) NOT NULL DEFAULT 0,
    price_source    VARCHAR(20)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- INVOICES
CREATE TABLE invoices (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(20)   NOT NULL UNIQUE,
    order_id       UUID NOT NULL UNIQUE REFERENCES orders(id),
    customer_id    UUID NOT NULL REFERENCES customers(id),
    issued_date    DATE NOT NULL,
    due_date       DATE,
    subtotal       NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_total      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    pdf_path       VARCHAR(500),
    print_count    INTEGER NOT NULL DEFAULT 0,
    status         VARCHAR(15) NOT NULL DEFAULT 'ISSUED',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID REFERENCES users(id)
);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_date     ON invoices(issued_date);

-- RETURNS
CREATE TABLE returns (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    return_number VARCHAR(20)   NOT NULL UNIQUE,
    customer_id   UUID NOT NULL REFERENCES customers(id),
    product_id    UUID NOT NULL REFERENCES products(id),
    order_id      UUID REFERENCES orders(id),
    quantity      NUMERIC(10,3) NOT NULL,
    reason        TEXT,
    status        VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    return_date   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    UUID REFERENCES users(id)
);

-- DAMAGES
CREATE TABLE damages (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    damage_number VARCHAR(20)   NOT NULL UNIQUE,
    customer_id   UUID NOT NULL REFERENCES customers(id),
    product_id    UUID NOT NULL REFERENCES products(id),
    quantity      NUMERIC(10,3) NOT NULL,
    remarks       TEXT,
    damage_date   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    UUID REFERENCES users(id)
);

-- CUSTOMER VISITS (GEO TRACKING)
CREATE TABLE customer_visits (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id  UUID NOT NULL REFERENCES customers(id),
    sales_rep_id UUID NOT NULL REFERENCES users(id),
    latitude     NUMERIC(10,7),
    longitude    NUMERIC(10,7),
    check_in     TIMESTAMPTZ,
    check_out    TIMESTAMPTZ,
    purpose      TEXT,
    geo_fenced   BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_visit_rep_date ON customer_visits(sales_rep_id, check_in);

-- SALES TARGETS
CREATE TABLE sales_targets (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sales_rep_id   UUID NOT NULL REFERENCES users(id),
    target_year    INTEGER NOT NULL,
    target_month   INTEGER NOT NULL,
    target_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    achieved_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    UNIQUE(sales_rep_id, target_year, target_month)
);

-- AUDIT LOGS
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID,
    changes     JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user   ON audit_logs(user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_time   ON audit_logs(created_at);
