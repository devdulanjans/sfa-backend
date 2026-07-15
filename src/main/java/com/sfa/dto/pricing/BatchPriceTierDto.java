package com.sfa.dto.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchPriceTierDto(
        UUID id,
        BigDecimal price,
        BigDecimal minQty,
        LocalDate startDate,
        LocalDate endDate,
        boolean customerSpecific,
        String label
) {}
