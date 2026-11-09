package com.sfa.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
    @NotNull(message = "Product is required") UUID productId,
    @NotNull @DecimalMin("0.001") BigDecimal quantity,
    BigDecimal discountPct,
    // Optional: the exact BatchPrice row the client picked from /pricing/tiers (e.g. the
    // mobile price-tier sheet). When present and still valid, this is used verbatim instead
    // of re-resolving "the best" batch price server-side — see PricingEngine.resolve.
    UUID batchPriceId,
    // Optional: the exact Promotion the client picked from /pricing/promotions, when more than
    // one was active for this product (see SystemSettingService#isPromotionManualSelectionEnabled).
    // When present and still active, used verbatim instead of auto-picking — see PricingEngine.resolve.
    UUID promotionId
) {}
