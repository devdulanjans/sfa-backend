package com.sfa.dto.tenant;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Bulk channel change for customers / products.
 *
 * @param tenantId  legacy single-channel form: "move into this channel" (same as SET with just it)
 * @param tenantIds the channels to add / remove / set (takes precedence over tenantId)
 * @param mode      ADD (make available in these channels too — default when tenantIds is given),
 *                  REMOVE (no longer available in them), or SET (exactly these, within reach)
 */
public record BulkAssignTenantRequest(
        UUID tenantId,
        List<UUID> tenantIds,
        String mode,
        @NotEmpty List<UUID> ids
) {
    public List<UUID> channels() {
        if (tenantIds != null && !tenantIds.isEmpty()) return tenantIds;
        return tenantId != null ? List.of(tenantId) : List.of();
    }

    public com.sfa.service.ChannelMembershipService.Mode resolvedMode() {
        if (mode != null && !mode.isBlank()) {
            return com.sfa.service.ChannelMembershipService.Mode.valueOf(mode.trim().toUpperCase());
        }
        return (tenantIds != null && !tenantIds.isEmpty())
                ? com.sfa.service.ChannelMembershipService.Mode.ADD
                : com.sfa.service.ChannelMembershipService.Mode.SET;
    }
}
