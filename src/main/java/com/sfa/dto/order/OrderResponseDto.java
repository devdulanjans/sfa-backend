package com.sfa.dto.order;

import com.sfa.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        String orderNumber,
        String status,
        String orderSource,
        CustomerSummary customer,
        SalesRepSummary salesRep,
        DistributorSummary distributor,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal total,
        String notes,
        Instant orderDate,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        String deliveryAddressLabel,
        String deliveryAddressLine,
        String customerSignature,
        String salespersonSignature,
        String invoiceNumber,
        List<OrderItemDto> items
) {
    public record CustomerSummary(UUID id, String name, String customerCode) {}
    public record SalesRepSummary(UUID id, String fullName, String email) {}
    public record DistributorSummary(UUID id, String name, String code) {}

    public record OrderItemDto(
            UUID id,
            ProductSummary product,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal taxPct,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String priceSource
    ) {
        public record ProductSummary(UUID id, String name, String productCode) {}
    }

    public static OrderResponseDto from(Order order) {
        return from(order, null);
    }

    public static OrderResponseDto from(Order order, String invoiceNumber) {
        var c = order.getCustomer();
        var s = order.getSalesRep();
        var d = order.getDistributor();
        return new OrderResponseDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getOrderSource().name(),
                c == null ? null : new CustomerSummary(c.getId(), c.getName(), c.getCustomerCode()),
                s == null ? null : new SalesRepSummary(s.getId(), s.getFullName(), s.getEmail()),
                d == null ? null : new DistributorSummary(d.getId(), d.getName(), d.getCode()),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getTotal(),
                order.getNotes(),
                order.getOrderDate(),
                order.getApprovedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeliveryAddressLabel(),
                order.getDeliveryAddressLine(),
                order.getCustomerSignature(),
                order.getSalespersonSignature(),
                invoiceNumber,
                order.getItems().stream().map(item -> {
                    var p = item.getProduct();
                    return new OrderItemDto(
                            item.getId(),
                            p == null ? null : new OrderItemDto.ProductSummary(
                                    p.getId(), p.getName(), p.getProductCode()),
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getDiscountPct(),
                            item.getTaxPct(),
                            item.getDiscountAmount(),
                            item.getTaxAmount(),
                            item.getLineTotal(),
                            item.getPriceSource()
                    );
                }).toList()
        );
    }
}
