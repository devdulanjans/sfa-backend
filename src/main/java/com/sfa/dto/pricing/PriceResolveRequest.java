package com.sfa.dto.pricing;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PriceResolveRequest(
        @NotNull UUID productId,
        UUID customerId
) {}
