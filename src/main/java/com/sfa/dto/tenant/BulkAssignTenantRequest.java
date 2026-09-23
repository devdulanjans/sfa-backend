package com.sfa.dto.tenant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkAssignTenantRequest(
        @NotNull UUID tenantId,
        @NotEmpty List<UUID> ids
) {}
