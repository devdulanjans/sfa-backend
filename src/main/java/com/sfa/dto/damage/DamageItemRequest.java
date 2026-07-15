package com.sfa.dto.damage;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DamageItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity
) {}
