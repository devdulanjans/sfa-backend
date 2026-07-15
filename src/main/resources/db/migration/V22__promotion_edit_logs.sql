CREATE TABLE promotion_edit_logs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id    UUID         NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    promotion_name  VARCHAR(200) NOT NULL,
    edited_by       UUID         REFERENCES users(id),
    edited_by_name  VARCHAR(200),
    changes_json    TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prom_edit_logs_promo ON promotion_edit_logs(promotion_id, created_at DESC);
