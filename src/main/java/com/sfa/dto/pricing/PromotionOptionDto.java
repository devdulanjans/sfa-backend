package com.sfa.dto.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/** One selectable promotion option — returned by {@code GET /api/pricing/promotions} when
 *  more than one active promotion matches a product+customer, so the client can let the
 *  sales rep choose (see SystemSettingService#isPromotionManualSelectionEnabled). */
public record PromotionOptionDto(
        UUID id,
        String name,
        String type,
        BigDecimal discountValue,
        UUID freeProductId,
        String freeProductName,
        Integer maxFreeCount,
        Integer minOrderQty
) {}
