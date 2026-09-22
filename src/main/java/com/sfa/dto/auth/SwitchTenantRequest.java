package com.sfa.dto.auth;

import java.util.UUID;

/** tenantId is nullable — SUPER_ADMIN passes null to exit a channel it entered and
 *  return to the normal unscoped platform view. Every other role must pass a real id;
 *  AuthService.switchTenant enforces that. */
public record SwitchTenantRequest(
    UUID tenantId
) {}
