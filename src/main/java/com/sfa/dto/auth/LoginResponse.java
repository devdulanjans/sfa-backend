package com.sfa.dto.auth;

import com.sfa.dto.distributor.DistributorDto;
import com.sfa.dto.tenant.TenantDto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String username,
    String email,
    String fullName,
    String role,
    UUID customerId,
    List<DistributorDto> distributors,
    List<String> permissions,
    boolean sfaEnabled,
    boolean posEnabled,
    boolean financeEnabled,
    boolean multiTenantEnabled,
    /** Every channel this account can operate in. For SUPER_ADMIN this is every channel
     *  on the platform (it isn't a "member" of any — it can enter any of them). */
    List<TenantDto> tenants,
    /** The channel this session is currently scoped to. Null for SUPER_ADMIN/PLATFORM_OWNER's
     *  normal unscoped platform view, or for a multi-channel user who hasn't picked one yet —
     *  in the latter case the client must call /api/auth/switch-tenant before other endpoints
     *  will serve data. SUPER_ADMIN can set this by entering a channel via the same endpoint. */
    TenantDto activeTenant,
    /** Set by an admin/super-admin password reset — the client must force this session
     *  straight to the change-password screen before anything else until it's cleared. */
    boolean mustChangePassword
) {}
