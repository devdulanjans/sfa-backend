package com.sfa.service;

import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which channel a newly-created (or re-channeled) tenant-scoped row should
 * belong to, for the handful of create flows where the caller may need to pick one
 * explicitly rather than always taking whatever's ambient in {@link TenantContext}:
 *
 *  - SUPER_ADMIN is always unscoped, so it must always say which channel explicitly.
 *  - Any other actor who manages more than one channel (e.g. an ADMIN over two channels)
 *    may also pick a specific one for this row, rather than being stuck with whichever
 *    channel their session happens to be active in right now.
 *  - A single-channel actor (or a multi-channel actor who didn't pass a tenantId at all)
 *    just gets the ambient ontext, same as before this existed — callers get {@code
 *    Optional.empty()} in that case and should leave the entity's tenant unset, letting
 *    TenantAwareEntityListener stamp it automatically on save().
 */
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public Optional<Tenant> resolveExplicitTenant(UUID requestedTenantId, UUID actingUserId, String entityLabel) {
        if (TenantContext.isUnscoped()) {
            if (requestedTenantId == null) {
                throw new BusinessException("Select a channel for this " + entityLabel + ".");
            }
            return Optional.of(findTenantOrThrow(requestedTenantId));
        }

        if (requestedTenantId == null) {
            return Optional.empty();
        }
        UUID ambientTenantId = TenantContext.getTenantId();
        if (requestedTenantId.equals(ambientTenantId)) {
            return Optional.empty();
        }

        // A different channel than the session's active one was requested — only allow
        // it if the actor is themselves a member of that channel.
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actingUserId));
        Tenant requested = findTenantOrThrow(requestedTenantId);
        boolean allowed = tenantRepository.findByUserId(actor.getId()).stream()
                .anyMatch(t -> t.getId().equals(requestedTenantId));
        if (!allowed) {
            throw new BusinessException("You don't have access to that channel.");
        }
        return Optional.of(requested);
    }

    private Tenant findTenantOrThrow(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }
}
