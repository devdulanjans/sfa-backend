package com.sfa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotNull UUID roleId,
        List<UUID> distributorIds,
        List<UUID> customerIds,
        @Size(min = 8) String password   // null = keep existing; non-null must be ≥ 8 chars
) {}
