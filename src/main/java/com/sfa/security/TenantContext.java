package com.sfa.security;

import java.util.UUID;

/**
 * Request-scoped holder for the active channel (tenant). Populated by
 * JwtAuthenticationFilter from the JWT's "tid" claim and cleared at the end
 * of every request — MUST be cleared, since the servlet container reuses
 * threads across requests.
 *
 * Three states a request can be in:
 *  - scoped:    getTenantId() returns the active tenant; TenantFilterInterceptor
 *               binds it into every tenant-scoped Hibernate query.
 *  - unscoped:  SUPER_ADMIN/PLATFORM_OWNER — no filter is applied, all channels visible.
 *  - pending:   an authenticated multi-channel user who hasn't picked one yet —
 *               every tenant-scoped read must be refused, not silently filtered.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID>    CURRENT   = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> UNSCOPED  = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(UUID tenantId) {
        CURRENT.set(tenantId);
        UNSCOPED.set(Boolean.FALSE);
    }

    public static void setUnscoped() {
        CURRENT.remove();
        UNSCOPED.set(Boolean.TRUE);
    }

    public static UUID getTenantId() {
        return CURRENT.get();
    }

    public static boolean isUnscoped() {
        return Boolean.TRUE.equals(UNSCOPED.get());
    }

    public static boolean isPending() {
        return !isUnscoped() && getTenantId() == null;
    }

    public static void clear() {
        CURRENT.remove();
        UNSCOPED.remove();
    }
}
