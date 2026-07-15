-- Sequence for POS sale numbers
CREATE SEQUENCE IF NOT EXISTS pos_sale_number_seq START WITH 1 INCREMENT BY 1;

-- POS sales header
CREATE TABLE pos_sales (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number     VARCHAR(50)     NOT NULL UNIQUE,
    customer_id     UUID            REFERENCES customers(id),
    customer_name   VARCHAR(200),
    payment_method  VARCHAR(20)     NOT NULL,
    subtotal        DECIMAL(12,2)   NOT NULL,
    discount_amount DECIMAL(12,2)   NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(12,2)   NOT NULL DEFAULT 0,
    total           DECIMAL(12,2)   NOT NULL,
    amount_tendered DECIMAL(12,2),
    change_amount   DECIMAL(12,2),
    status          VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    notes           TEXT,
    created_by      UUID            NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pos_sales_created_by ON pos_sales(created_by, created_at DESC);
CREATE INDEX idx_pos_sales_customer   ON pos_sales(customer_id);
CREATE INDEX idx_pos_sales_created_at ON pos_sales(created_at DESC);

-- POS sale line items
CREATE TABLE pos_sale_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id         UUID            NOT NULL REFERENCES pos_sales(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL REFERENCES products(id),
    product_name    VARCHAR(200)    NOT NULL,
    quantity        DECIMAL(10,3)   NOT NULL,
    unit_price      DECIMAL(12,2)   NOT NULL,
    discount_pct    DECIMAL(5,2)    NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12,2)   NOT NULL DEFAULT 0,
    tax_pct         DECIMAL(5,2)    NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(12,2)   NOT NULL DEFAULT 0,
    line_total      DECIMAL(12,2)   NOT NULL
);

CREATE INDEX idx_pos_sale_items_sale ON pos_sale_items(sale_id);
