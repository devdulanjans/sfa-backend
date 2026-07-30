package com.sfa.dto;

import com.sfa.entity.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FixedAssetDto(
        UUID id,
        String assetCode,
        String name,
        String category,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        BigDecimal salvageValue,
        int usefulLifeYears,
        BigDecimal accumulatedDepreciation,
        BigDecimal netBookValue,
        boolean active
) {
    public static FixedAssetDto from(FixedAsset a) {
        return new FixedAssetDto(
                a.getId(), a.getAssetCode(), a.getName(), a.getCategory(), a.getPurchaseDate(),
                a.getPurchaseCost(), a.getSalvageValue(), a.getUsefulLifeYears(), a.getAccumulatedDepreciation(),
                a.getPurchaseCost().subtract(a.getAccumulatedDepreciation()), a.isActive());
    }
}
