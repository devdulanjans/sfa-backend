package com.sfa.entity;

/**
 * Implemented by every entity owned by a single tenant/channel. Lets
 * TenantAwareEntityListener stamp the owning tenant on create without
 * per-entity boilerplate.
 */
public interface TenantScoped {
    Tenant getTenant();
    void setTenant(Tenant tenant);
}
