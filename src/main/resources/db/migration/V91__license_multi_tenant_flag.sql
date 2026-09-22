-- Multi-channel is its own licensed package, controlled from the Platform Owner's
-- License screen alongside SFA/POS/Finance. Defaults to ON so this install (already
-- using channels) keeps working exactly as it does today.
ALTER TABLE license_settings ADD COLUMN multi_tenant_enabled BOOLEAN NOT NULL DEFAULT TRUE;
