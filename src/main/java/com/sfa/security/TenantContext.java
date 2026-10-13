package com.sfa.security;

import java.util.Set;
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
 *
 * A scoped request also carries its set of VISIBLE channels. That's just the active
 * channel for every normal session; a customer-portal session widens it to every
 * channel the customer can order from, so its catalog/orders/promotions span them all
 * while new rows are still stamped with the single active channel.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID>    CURRENT   = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> UNSCOPED  = new ThreadLocal<>();
    private static final ThreadLocal<Set<UUID>> VISIBLE = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(UUID tenantId) {
        setTenant(tenantId, tenantId != null ? Set.of(tenantId) : Set.of());
    }

    /** Active channel plus a wider set of channels this request may read from —
     *  always includes the active one itself. */
    public static void setTenant(UUID tenantId, Set<UUID> visibleTenantIds) {
        CURRENT.set(tenantId);
        UNSCOPED.set(Boolean.FALSE);
        if (tenantId == null) {
            VISIBLE.set(Set.of());
        } else {
            Set<UUID> visible = new java.util.LinkedHashSet<>(visibleTenantIds);
            visible.add(tenantId);
            VISIBLE.set(Set.copyOf(visible));
        }
    }

    public static void setUnscoped() {
        CURRENT.remove();
        VISIBLE.remove();
        UNSCOPED.set(Boolean.TRUE);
    }

    public static UUID getTenantId() {
        return CURRENT.get();
    }

    /** Channels a scoped request may read from; empty when unscoped or pending. */
    public static Set<UUID> getVisibleTenantIds() {
        Set<UUID> visible = VISIBLE.get();
        return visible != null ? visible : Set.of();
    }

    public static boolean isVisible(UUID tenantId) {
        return tenantId != null && getVisibleTenantIds().contains(tenantId);
    }

    public static boolean isUnscoped() {
        return Boolean.TRUE.equals(UNSCOPED.get());
    }

    public static boolean isPending() {
        return !isUnscoped() && getTenantId() == null;
    }

    public static void clear() {
        CURRENT.remove();
        VISIBLE.remove();
        UNSCOPED.remove();
    }
}
