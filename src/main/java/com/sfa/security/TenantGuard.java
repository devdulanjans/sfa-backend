package com.sfa.security;

import com.sfa.entity.TenantScoped;
import com.sfa.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Closes the gap Hibernate's {@code @Filter} leaves open: the filter only rewrites
 * HQL/Criteria queries, NOT a primary-key lookup (EntityManager.find / Session.get /
 * Spring Data's findById) — so a plain {@code repo.findById(id)} on a TenantScoped
 * entity returns the row regardless of channel, even with the filter enabled on the
 * session. Every single-entity fetch (and any update/delete built on one) MUST run
 * its result through {@link #requireVisible} before returning or mutating it, the
 * same way list/search endpoints already get channel-scoping for free from the filter.
 */
public final class TenantGuard {

    private TenantGuard() {}

    /**
     * @return the entity, if the current request may see it.
     * @throws ResourceNotFoundException — not a 403 — if it belongs to a different
     *         channel than the caller's active one, or the caller hasn't picked a
     *         channel yet. A 404 doesn't confirm to the caller that the id exists at
     *         all in a channel they can't see.
     */
    public static <T extends TenantScoped> T requireVisible(T entity, String entityName, UUID id) {
        if (entity == null || TenantContext.isUnscoped()) {
            return entity;
        }
        UUID ambientTenantId = TenantContext.getTenantId(); // null when the caller hasn't picked a channel yet
        UUID entityTenantId  = entity.getTenant() != null ? entity.getTenant().getId() : null;
        if (ambientTenantId == null || !ambientTenantId.equals(entityTenantId)) {
            throw new ResourceNotFoundException(entityName, id);
        }
        return entity;
    }
}
