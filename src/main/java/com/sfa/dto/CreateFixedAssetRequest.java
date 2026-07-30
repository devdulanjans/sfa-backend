package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFixedAssetRequest(
        String assetCode,
        String name,
        String category,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        BigDecimal salvageValue,
        Integer usefulLifeYears,
        UUID bankAccountId
) {}
