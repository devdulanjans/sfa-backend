package com.sfa.dto.ret;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        // Optional: the exact BatchPrice row the client picked from /pricing/tiers (e.g. the
        // mobile batch-price sheet). When present and still valid, this is used verbatim instead
        // of re-resolving "the best" batch price server-side — see PricingEngine.resolve.
        UUID batchPriceId
) {}
