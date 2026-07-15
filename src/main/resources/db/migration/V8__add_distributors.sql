-- ============================================================
-- Distributor Management
-- Flyway Migration V8
-- ============================================================

CREATE TABLE distributors (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    address     TEXT,
    phone       VARCHAR(20),
    email       VARCHAR(120),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX idx_distributors_code   ON distributors(code);
CREATE INDEX idx_distributors_status ON distributors(status);

-- Many-to-many: users <-> distributors
CREATE TABLE user_distributors (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, distributor_id)
);
CREATE INDEX idx_ud_distributor ON user_distributors(distributor_id);
CREATE INDEX idx_ud_user        ON user_distributors(user_id);

-- Seed a default distributor so existing users aren't left unassigned
INSERT INTO distributors (code, name, status) VALUES ('DEFAULT', 'Default Distributor', 'ACTIVE');
