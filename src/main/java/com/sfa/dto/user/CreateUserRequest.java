package com.sfa.dto.user;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 8) String password,
        @NotNull UUID roleId,
        List<UUID> distributorIds,
        List<UUID> customerIds,
        UUID customerId       // for CUSTOMER role: the single customer this user represents
) {}
