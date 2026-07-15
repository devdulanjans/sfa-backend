-- Track the last 4 digits of the card used for CARD payment POS sales
ALTER TABLE pos_sales ADD COLUMN card_last4 VARCHAR(4);

ALTER TABLE pos_sales ADD CONSTRAINT chk_pos_sales_card_last4_digits
    CHECK (card_last4 IS NULL OR card_last4 ~ '^[0-9]{4}$');
