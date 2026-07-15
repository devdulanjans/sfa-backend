package com.sfa.dto.geo;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CheckInRequest(
        @NotNull UUID customerId,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {}
