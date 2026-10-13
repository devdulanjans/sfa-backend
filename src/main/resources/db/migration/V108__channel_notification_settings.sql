-- Customer email / WhatsApp notifications are configured per channel (tenant): each channel can
-- send from its own mailbox and WhatsApp business number. Blank SMTP host / WhatsApp phone-number
-- id = use the server default from the SMTP_* / WHATSAPP_* env vars.
CREATE TABLE channel_notification_settings (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                     UUID NOT NULL REFERENCES tenants(id),
    email_enabled                 BOOLEAN NOT NULL DEFAULT FALSE,
    smtp_host                     VARCHAR(200),
    smtp_port                     INTEGER,
    smtp_username                 VARCHAR(200),
    smtp_password_enc             TEXT,
    smtp_security                 VARCHAR(10) NOT NULL DEFAULT 'STARTTLS',
    from_email                    VARCHAR(200),
    from_name                     VARCHAR(200),
    whatsapp_enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_phone_number_id      VARCHAR(100),
    whatsapp_access_token_enc     TEXT,
    whatsapp_template_name        VARCHAR(100),
    whatsapp_template_language    VARCHAR(20),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                    UUID,
    CONSTRAINT uk_channel_notification_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_channel_notification_smtp_security CHECK (smtp_security IN ('STARTTLS', 'SSL', 'NONE'))
);

-- Carry the short-lived global toggles from V107 over to every existing channel, then drop them.
INSERT INTO channel_notification_settings (tenant_id, email_enabled, whatsapp_enabled)
SELECT t.id,
       COALESCE((SELECT value = 'true' FROM system_settings WHERE key = 'notification_email_enabled'), FALSE),
       COALESCE((SELECT value = 'true' FROM system_settings WHERE key = 'notification_whatsapp_enabled'), FALSE)
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;

DELETE FROM system_settings WHERE key IN ('notification_email_enabled', 'notification_whatsapp_enabled');

INSERT INTO system_modules (code, name, url, icon, parent_code, sort_order) VALUES
('MOD_SETTINGS_NOTIFICATIONS', 'Notifications', '/settings/notifications', '✉️', 'MOD_SETTINGS', 7)
ON CONFLICT (code) DO NOTHING;
