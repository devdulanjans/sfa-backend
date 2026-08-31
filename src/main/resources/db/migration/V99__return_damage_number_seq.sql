-- Sequences backing the new RTN_IT{location}_{seq} / DMG_IT{location}_{seq}
-- return/damage number format, replacing the previous UUID-suffix scheme.
CREATE SEQUENCE IF NOT EXISTS return_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS damage_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO CYCLE;
