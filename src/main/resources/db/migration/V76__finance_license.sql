-- Financial Management is a new, not-yet-sold module — default OFF so no existing
-- install is silently granted it for free. Platform owners opt in per-install via
-- the existing /platform/license screen, same as SFA/POS.
ALTER TABLE license_settings ADD COLUMN finance_enabled BOOLEAN NOT NULL DEFAULT FALSE;
