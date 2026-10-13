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
        FreeProduct freeProduct,
        // Pre-promotion price, equal to unitPrice unless a promotion discounted it — lets the
        // client show the discount a promotion applied.
        BigDecimal  basePrice
) {
    /**
     * Populated only when a FREE_PRODUCT promotion applies to the resolved product.
     * @param promotionId the FREE_PRODUCT promotion's own id — lets the client exclude it
     *                     locally when a different, explicit promotion is chosen for this same
     *                     product's paid line (mirrors the backend's own exclusion at submit).
     */
    public record FreeProduct(UUID id, String name, String productCode,
                              int maxFreeCount, int minOrderQty, List<UUID> applicableProductIds,
                              UUID promotionId) {}
}
