package com.sfa.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
    @NotNull(message = "Product is required") UUID productId,
    @NotNull @DecimalMin("0.001") BigDecimal quantity,
    BigDecimal discountPct
) {}
