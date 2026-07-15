package com.sfa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
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

    // Position within the order, in the order items were added to the cart —
    // needed because UUID primary keys don't preserve insertion order, and the
    // invoice number's category code is derived from the first item added.
    @Column(name = "line_no", nullable = false)
    @Builder.Default
    private Integer lineNo = 0;

    public void calculate() {
        BigDecimal gross = unitPrice.multiply(quantity);
        discountAmount = gross.multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal afterDiscount = gross.subtract(discountAmount);
        taxAmount = afterDiscount.multiply(taxPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        lineTotal = afterDiscount.add(taxAmount);
    }
}
