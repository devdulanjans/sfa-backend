package com.sfa.dto.damage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateDamageRequest(
        @NotNull UUID customerId,
        @NotEmpty @Valid List<DamageItemRequest> items,
        @NotBlank String description
) {}
