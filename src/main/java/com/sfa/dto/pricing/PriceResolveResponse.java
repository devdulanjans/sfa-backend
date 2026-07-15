package com.sfa.dto.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PriceResolveResponse(
        BigDecimal  unitPrice,
        String      priceSource,
        String      promotionName,
        BigDecimal  maxDiscountAmount,
        BigDecimal  taxPct,
        FreeProduct freeProduct
) {
    /** Populated only when a FREE_PRODUCT promotion applies to the resolved product. */
    public record FreeProduct(UUID id, String name, String productCode,
                              int maxFreeCount, int minOrderQty, List<UUID> applicableProductIds) {}
}
