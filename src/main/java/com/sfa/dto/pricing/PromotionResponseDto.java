package com.sfa.dto.pricing;

import com.sfa.entity.Promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PromotionResponseDto(
        UUID id,
        String name,
        String type,
        BigDecimal discountValue,
        List<ProductSummary> products,
        CustomerSummary customer,
        ProductSummary freeProduct,
        Integer maxFreeCount,
        Integer minOrderQty,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public record ProductSummary(UUID id, String name, String productCode) {}
    public record CustomerSummary(UUID id, String name, String customerCode) {}

    public static PromotionResponseDto from(Promotion p) {
        var c  = p.getCustomer();
        var fp = p.getFreeProduct();
        return new PromotionResponseDto(
                p.getId(),
                p.getName(),
                p.getType().name(),
                p.getDiscountValue(),
                p.getProducts().stream()
                        .map(pr -> new ProductSummary(
                                pr.getId(), pr.getName(), pr.getProductCode()))
                        .toList(),
                c  == null ? null : new CustomerSummary(c.getId(), c.getName(), c.getCustomerCode()),
                fp == null ? null : new ProductSummary(fp.getId(), fp.getName(), fp.getProductCode()),
                p.getMaxFreeCount(),
                p.getMinOrderQty(),
                p.getStartDate(),
                p.getEndDate(),
                p.getIsActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
