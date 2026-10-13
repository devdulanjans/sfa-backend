package com.sfa.dto.pricing;

import com.sfa.entity.Product;
import com.sfa.entity.Promotion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A promotion as the customer app shows it — everything needed to spell out its rules to the
 * customer, mirroring exactly what PricingEngine enforces:
 * <ul>
 *   <li>PERCENTAGE — {@code discountValue}% off the unit price.</li>
 *   <li>FIXED_AMOUNT — LKR {@code discountValue} off the whole line, only once the line is worth
 *       more than that amount.</li>
 *   <li>FREE_PRODUCT — buying at least {@code minOrderQty} (combined) of {@code products} earns
 *       {@code maxFreeCount} × {@code freeProductName}, once per order.</li>
 * </ul>
 * {@code minOrderQty} / {@code maxFreeCount} are only set for FREE_PRODUCT — the other types
 * have no quantity rule.
 *
 * @param audience FOR_YOU (this customer only), YOUR_GROUP (their customer group) or EVERYONE
 * @param products products the promotion applies to — direct ones plus its product group's members
 */
public record CustomerPromotionDto(
        UUID id,
        String name,
        String type,
        BigDecimal discountValue,
        Integer minOrderQty,
        String startDate,
        String endDate,
        List<ProductItem> products,
        UUID freeProductId,
        String freeProductName,
        String freeProductCode,
        Integer maxFreeCount,
        String audience,
        String tenantName
) {
    public record ProductItem(String id, String name, String productCode) {}

    public static CustomerPromotionDto from(Promotion p) {
        Map<UUID, Product> products = new LinkedHashMap<>();
        p.getProducts().forEach(pr -> products.put(pr.getId(), pr));
        if (p.getProductGroup() != null) {
            p.getProductGroup().getMembers().forEach(pr -> products.putIfAbsent(pr.getId(), pr));
        }
        boolean freeType = p.getType() == Promotion.PromotionType.FREE_PRODUCT;
        Product free = p.getFreeProduct();
        return new CustomerPromotionDto(
                p.getId(),
                p.getName(),
                p.getType().name(),
                p.getDiscountValue(),
                freeType ? p.getMinOrderQty() : null,
                p.getStartDate().toString(),
                p.getEndDate().toString(),
                products.values().stream()
                        .map(pr -> new ProductItem(pr.getId().toString(), pr.getName(), pr.getProductCode()))
                        .toList(),
                free != null ? free.getId() : null,
                free != null ? free.getName() : null,
                free != null ? free.getProductCode() : null,
                freeType ? p.getMaxFreeCount() : null,
                p.getCustomer() != null ? "FOR_YOU" : p.getCustomerGroup() != null ? "YOUR_GROUP" : "EVERYONE",
                p.getTenant() != null ? p.getTenant().getName() : null);
    }
}
