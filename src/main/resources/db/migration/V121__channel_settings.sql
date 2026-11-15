-- General settings per channel. system_settings keeps the platform defaults (and the list of
-- settings that exist); a channel_settings row overrides one setting for one channel. A channel
-- with no row for a key uses the platform default.
CREATE TABLE channel_settings (
    tenant_id   UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key         VARCHAR(100) NOT NULL REFERENCES system_settings(key) ON DELETE CASCADE,
    value       VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  UUID,
    PRIMARY KEY (tenant_id, key)
);
