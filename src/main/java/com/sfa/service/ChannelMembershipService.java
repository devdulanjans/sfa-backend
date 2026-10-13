package com.sfa.service;

import com.sfa.entity.MultiChannelScoped;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Which channels a customer / product / promotion / batch price is available in (V112).
 *
 * Rules, shared by every create/edit/bulk path:
 *  - An unscoped SUPER_ADMIN may grant or revoke any channel. Anyone else only the channels
 *    they themselves belong to — channels outside their reach are never touched by their edits.
 *  - A row always keeps at least one channel.
 *  - The home channel (tenant_id) stays in the set; if it's removed, the first remaining channel
 *    becomes home.
 */
@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    public enum Mode { ADD, REMOVE, SET }

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    /** Channels the acting user may assign: every channel for a SUPER_ADMIN (even one who has
     *  entered a specific channel) or any unscoped session, else the user's own channels. */
    public Set<UUID> manageableChannels(UUID actingUserId) {
        boolean superAdmin = actingUserId != null && userRepository.findById(actingUserId)
                .map(u -> Role.SUPER_ADMIN.equals(u.getRole().getName())).orElse(false);
        if (superAdmin || TenantContext.isUnscoped()) {
            return tenantRepository.findAll().stream().map(Tenant::getId).collect(Collectors.toSet());
        }
        return tenantRepository.findByUserId(actingUserId).stream().map(Tenant::getId).collect(Collectors.toSet());
    }

    /** Replaces the row's channels with {@code requested} (a null list leaves them unchanged). */
    public void setChannels(MultiChannelScoped row, Collection<UUID> requested, UUID actingUserId) {
        if (requested == null) return;
        modify(row, requested, Mode.SET, actingUserId);
    }

    /** ADD / REMOVE / SET {@code ids} on the row, within the actor's reach. */
    public void modify(MultiChannelScoped row, Collection<UUID> ids, Mode mode, UUID actingUserId) {
        Set<UUID> requested = new LinkedHashSet<>(ids == null ? List.of() : ids);
        Set<UUID> manageable = manageableChannels(actingUserId);
        Map<UUID, Tenant> tenants = new HashMap<>();
        tenantRepository.findAllById(requested).forEach(t -> tenants.put(t.getId(), t));
        for (UUID id : requested) {
            if (!tenants.containsKey(id)) throw new BusinessException("Channel not found: " + id);
            if (!manageable.contains(id)) {
                throw new BusinessException("You can only assign channels you belong to (" + tenants.get(id).getName() + ").");
            }
        }

        Set<UUID> result = new LinkedHashSet<>(row.getChannelIds());
        switch (mode) {
            case ADD -> result.addAll(requested);
            case REMOVE -> result.removeAll(requested);
            case SET -> {
                result.removeIf(manageable::contains);   // keep what the actor can't manage
                result.addAll(requested);
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException("It must stay available in at least one channel.");
        }
        row.getChannelIds().clear();
        row.getChannelIds().addAll(result);

        if (row.getTenant() == null || !result.contains(row.getTenant().getId())) {
            UUID home = requested.stream().filter(result::contains).findFirst().orElse(result.iterator().next());
            row.setTenant(tenants.containsKey(home) ? tenants.get(home)
                    : tenantRepository.findById(home).orElseThrow());
        }
    }

    /** For a brand-new row: its channels are {@code requested} when given (home = {@code home} if
     *  it's among them, else the first), otherwise it's left for the entity's @PrePersist to
     *  default to its home channel. */
    public void initChannels(MultiChannelScoped row, Collection<UUID> requested, UUID actingUserId) {
        if (requested == null || requested.isEmpty()) return;
        modify(row, requested, Mode.SET, actingUserId);
    }
}
