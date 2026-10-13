package com.sfa.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a block of work as if the request were scoped to exactly one channel, then puts the
 * request's original scope back. Used where a request that can see several channels (a
 * customer-portal session) has to create rows belonging to one specific channel — e.g. one
 * order per channel at checkout — so pricing/promotion lookups and the
 * TenantAwareEntityListener stamp only see that channel.
 */
@Component
public class TenantScope {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T callInChannel(UUID tenantId, Supplier<T> work) {
        boolean   wasUnscoped = TenantContext.isUnscoped();
        UUID      prevActive  = TenantContext.getTenantId();
        Set<UUID> prevVisible = TenantContext.getVisibleTenantIds();

        Session session = entityManager.unwrap(Session.class);
        TenantContext.setTenant(tenantId);
        TenantFilterInterceptor.bindFilter(session);
        try {
            return work.get();
        } finally {
            if (wasUnscoped) {
                TenantContext.setUnscoped();
            } else {
                TenantContext.setTenant(prevActive, prevVisible);
            }
            if (wasUnscoped || prevActive != null) {
                TenantFilterInterceptor.bindFilter(session);
            } else {
                session.disableFilter("tenantFilter");
            }
        }
    }
}
