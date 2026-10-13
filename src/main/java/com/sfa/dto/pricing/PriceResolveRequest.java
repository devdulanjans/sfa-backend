package com.sfa.dto.pricing;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PriceResolveRequest(
        @NotNull UUID productId,
        UUID customerId,
        // Optional: an explicit Promotion the caller already picked from
        // GET /pricing/promotions, when more than one was active — see PricingEngine.resolve.
        UUID promotionId,
        // Optional, defaults to 1 — matters for a FIXED_AMOUNT promotion, whose per-unit price
        // depends on qty (a flat amount off the whole line, not per unit — see
        // PricingEngine.applyPromotion), and for any qty-gated batch-price tier.
        BigDecimal qty,
        // Optional: a specific BatchPrice tier the caller already picked (e.g. the mobile
        // price-tier sheet) — combined with promotionId so picking a tier never silently
        // discards a promotion also chosen for the same line. See PricingEngine.resolve.
        UUID batchPriceId
) {}
