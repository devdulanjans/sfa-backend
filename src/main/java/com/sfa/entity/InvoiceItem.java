package com.sfa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The part of one order line that an invoice charges for (V120). An order can be invoiced in
 * several goes, so an invoice prints these lines — never the order's own lines, whose
 * quantities are what was ordered, not what was delivered and billed.
 */
@Entity
@Table(name = "invoice_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 5)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "tax_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "price_source", length = 20)
    private String priceSource;

    @Column(name = "promotion_name", length = 150)
    private String promotionName;

    @Column(name = "line_no", nullable = false)
    @Builder.Default
    private Integer lineNo = 0;

    /** The order line it bills for — exposed by id so the admin can match lines. */
    public UUID getOrderItemId() {
        return orderItem != null ? orderItem.getId() : null;
    }

    /**
     * A detached, never-persisted OrderItem with this line's billed figures — lets the PDF,
     * thermal and export code (written against OrderItem) print the invoice unchanged.
     */
    public OrderItem asPrintLine() {
        return OrderItem.builder()
                .id(orderItem != null ? orderItem.getId() : id)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountPct(discountPct)
                .taxPct(taxPct)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .lineTotal(lineTotal)
                .priceSource(priceSource)
                .promotionName(promotionName)
                .lineNo(lineNo)
                .build();
    }
}
