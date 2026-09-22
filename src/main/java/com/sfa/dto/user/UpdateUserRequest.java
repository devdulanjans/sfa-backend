package com.sfa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// Password is deliberately NOT here — it's its own dedicated flow (self-service
// change-password, or an admin/super-admin's reset-password action), each with its
// own history check and audit trail, not bundled into general profile editing.
public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotNull UUID roleId,
        List<UUID> distributorIds,
        List<UUID> customerIds,
        List<UUID> customerGroupIds,
        List<UUID> tenantIds
) {}
