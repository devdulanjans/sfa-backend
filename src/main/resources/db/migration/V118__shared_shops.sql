-- Shared shop identity (public SaaS). A real-world shop such as "Menka Stores" is one row
-- here; every business that sells to it keeps its OWN customer row (code, credit limit,
-- balance, category, route, prices) pointing at the shop via customers.shop_id. Businesses
-- never see each other's customer rows — only the shared profile, when adding the shop.

CREATE TABLE shops (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(200) NOT NULL,
    phone          VARCHAR(30),
    -- Normalised to the local 0XXXXXXXXX form; NULL for empty/placeholder numbers
    -- (e.g. 0000000000), which must never be used to match shops.
    phone_norm     VARCHAR(10),
    whatsapp_norm  VARCHAR(10),
    email          VARCHAR(150),
    address        VARCHAR(255),
    area           VARCHAR(150),
    tax_number     VARCHAR(30),
    -- Upper-case letters/digits only; identifies a COMPANY (chain branches share it), so it
    -- only ever produces suggestions, never an automatic link.
    tax_norm       VARCHAR(30),
    latitude       NUMERIC(10,7),
    longitude      NUMERIC(10,7),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    legacy_customer_id UUID
);

-- Same rules as ShopService.normalizePhone.
CREATE FUNCTION pg_temp.norm_phone(p TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
             WHEN n ~ '^0[1-9][0-9]{8}$' AND n !~ '^0([0-9])\1{8}$' AND n <> '0123456789' THEN n
           END
      FROM (SELECT CASE
                     WHEN d ~ '^94[0-9]{9}$' THEN '0' || substr(d, 3)
                     WHEN d ~ '^[1-9][0-9]{8}$' THEN '0' || d
                     ELSE d
                   END AS n
              FROM (SELECT regexp_replace(coalesce(p, ''), '[^0-9]', '', 'g') AS d) x) y
$$;

-- Existing customers each get their own shop. They are NOT merged automatically: the data has
-- placeholder phones and chain-wide tax numbers, so merges are left to people.
INSERT INTO shops (name, phone, phone_norm, whatsapp_norm, email, address, area, tax_number, tax_norm,
                   latitude, longitude, created_at, legacy_customer_id)
SELECT c.name, c.phone, pg_temp.norm_phone(c.phone), pg_temp.norm_phone(c.whatsapp_number), c.email,
       c.location, c.place_of_supplier, c.tax_number,
       NULLIF(upper(regexp_replace(coalesce(c.tax_number, ''), '[^A-Za-z0-9]', '', 'g')), ''),
       c.latitude, c.longitude, c.created_at, c.id
  FROM customers c;

UPDATE shops SET tax_norm = NULL WHERE length(tax_norm) < 5;

ALTER TABLE customers ADD COLUMN shop_id UUID REFERENCES shops(id);
UPDATE customers c SET shop_id = s.id FROM shops s WHERE s.legacy_customer_id = c.id;
ALTER TABLE shops DROP COLUMN legacy_customer_id;

CREATE INDEX idx_customers_shop      ON customers(shop_id);
CREATE INDEX idx_shops_phone_norm    ON shops(phone_norm)    WHERE phone_norm IS NOT NULL;
CREATE INDEX idx_shops_whatsapp_norm ON shops(whatsapp_norm) WHERE whatsapp_norm IS NOT NULL;
CREATE INDEX idx_shops_tax_norm      ON shops(tax_norm)      WHERE tax_norm IS NOT NULL;
CREATE INDEX idx_shops_lat_lng       ON shops(latitude, longitude) WHERE latitude IS NOT NULL;

-- Codes only need to be unique within a business — the per-channel constraints
-- (uk_customers_tenant_code, uk_products_tenant_code) already exist.
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_code_key;
ALTER TABLE products  DROP CONSTRAINT IF EXISTS products_code_key;
