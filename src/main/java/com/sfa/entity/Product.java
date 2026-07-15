package com.sfa.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_code",     columnList = "product_code"),
    @Index(name = "idx_products_status",   columnList = "status"),
    @Index(name = "idx_products_category", columnList = "category_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_code", nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "default_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal defaultPrice = BigDecimal.ZERO;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("15.00");

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal maxDiscountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @JsonIgnore
    @ManyToMany(mappedBy = "assignedProducts", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Customer> assignedCustomers = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ProductStatus { ACTIVE, INACTIVE, DISCONTINUED }
}
