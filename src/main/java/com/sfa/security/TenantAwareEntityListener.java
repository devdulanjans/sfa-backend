package com.sfa.security;

import com.sfa.config.SpringContextHolder;
import com.sfa.entity.Tenant;
import com.sfa.entity.TenantScoped;
import com.sfa.repository.TenantRepository;
import jakarta.persistence.PrePersist;

import java.util.UUID;

/**
 * Stamps the owning tenant onto a new TenantScoped row from TenantContext, so
 * normal (single-channel-context) create flows never have to set it by hand.
 *
 * Doesn't help SUPER_ADMIN/PLATFORM_OWNER flows, which run unscoped — those
 * callers must set the tenant explicitly before save().
 */
public class TenantAwareEntityListener {

    @PrePersist
    public void beforeCreate(Object entity) {
        if (!(entity instanceof TenantScoped scoped) || scoped.getTenant() != null) {
            return;
        }
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "Cannot create " + entity.getClass().getSimpleName()
                            + " without a tenant — no active channel in this request context "
                            + "and none was set explicitly.");
        }
        // findById, not getReferenceById: a caller reading tenant.getCode()/getName() off the
        // freshly-saved entity later in the SAME request (e.g. building a response DTO) would
        // force-initialize an uninitialized proxy right as save()'s pending INSERT flushes —
        // and if that races the @Async audit logger touching the same lazy proxy on another
        // thread, Hibernate's internal state gets corrupted ("Illegal pop() with non-matching
        // JdbcValuesSourceProcessingState"). A real SELECT here avoids the proxy entirely.
        Tenant tenant = SpringContextHolder.getBean(TenantRepository.class).findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant no longer exists: " + tenantId));
        scoped.setTenant(tenant);
    }
}
