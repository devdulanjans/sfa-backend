package com.sfa.dto.product;

import com.sfa.dto.pricing.CustomerPromotionDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One product in the customer app's catalog, with the single price that customer pays for one
 * unit — resolved server-side by the PricingEngine (customer price → promotion → batch price →
 * default), so the customer never sees or picks between batch-price tiers.
 *
 * @param price         what one unit costs this customer today, after any promotion
 * @param basePrice     the price before a promotion — equal to {@code price} when none applies
 * @param priceSource   CUSTOMER_PRICE / PROMOTION / BATCH_PRICE / DEFAULT_PRICE
 * @param promotionName the applied promotion, if any
 * @param hasQtyPricing true when a larger quantity may get a lower tier price, so the app can
 *                      say the cart shows the final price
 * @param promotions    every promotion active on this product for this customer, with its rules;
 *                      when there is more than one, the customer picks which one to apply
 */
public record CustomerCatalogItemDto(
        ProductDto product,
        BigDecimal price,
        BigDecimal basePrice,
        String priceSource,
        String promotionName,
        BigDecimal taxPct,
        boolean hasQtyPricing,
        List<CustomerPromotionDto> promotions,
        /** The channel this product is ordered (and priced) in at checkout — may differ from the
         *  product's home channel now that a product can be in several (V112). */
        java.util.UUID orderTenantId,
        String orderTenantName
) {}
