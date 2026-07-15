CREATE TABLE system_settings (
    key         VARCHAR(100)  PRIMARY KEY,
    value       VARCHAR(500)  NOT NULL,
    description VARCHAR(500),
    updated_at  TIMESTAMPTZ,
    updated_by  UUID REFERENCES users(id)
);

INSERT INTO system_settings (key, value, description) VALUES
('isOrderPrevent', 'false', 'When true, an invoice is automatically generated the moment an order is created, skipping the manual approval and invoice steps.');
