package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_number",      columnList = "order_number"),
    @Index(name = "idx_orders_customer",    columnList = "customer_id"),
    @Index(name = "idx_orders_rep_status",  columnList = "sales_rep_id,status"),
    @Index(name = "idx_orders_date",        columnList = "order_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private User salesRep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", nullable = false, length = 20)
    @Builder.Default
    private OrderSource orderSource = OrderSource.SALES_REP;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "delivery_address_label", length = 100)
    private String deliveryAddressLabel;

    @Column(name = "delivery_address_line", columnDefinition = "text")
    private String deliveryAddressLine;

    @Column(name = "customer_signature", columnDefinition = "text")
    private String customerSignature;

    @Column(name = "salesperson_signature", columnDefinition = "text")
    private String salespersonSignature;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void recalculateTotals() {
        subtotal       = items.stream().map(i -> i.getUnitPrice().multiply(i.getQuantity()))
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
        discountAmount = items.stream().map(OrderItem::getDiscountAmount)
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount      = items.stream().map(OrderItem::getTaxAmount)
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
        total          = subtotal.subtract(discountAmount).add(taxAmount);
    }

    public enum OrderStatus { DRAFT, SUBMITTED, APPROVED, INVOICED, CANCELLED }
    public enum OrderSource { SALES_REP, CUSTOMER_APP }
}
