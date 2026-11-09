package com.sfa.dto.pricing;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PriceResolveRequest(
        @NotNull UUID productId,
        UUID customerId,
        // Optional: an explicit Promotion the caller already picked from
        // GET /pricing/promotions, when more than one was active — see PricingEngine.resolve.
        UUID promotionId
) {}
