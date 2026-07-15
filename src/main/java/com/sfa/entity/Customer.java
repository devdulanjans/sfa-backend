package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customers_code",   columnList = "customer_code"),
    @Index(name = "idx_customers_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_code", nullable = false, unique = true, length = 30)
    private String customerCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 100)
    private String location;

    @Column(name = "tax_number", length = 30)
    private String taxNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type")
    @Builder.Default
    private TaxType taxType = TaxType.STANDARD;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("18.00");

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CustomerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_rule", nullable = false)
    @Builder.Default
    private VisibilityRule visibilityRule = VisibilityRule.ALL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "credit_days")
    private Integer creditDays;

    @Column(name = "current_balance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerSource source = CustomerSource.MANUAL;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "customer_products",
        joinColumns = @JoinColumn(name = "customer_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private Set<Product> assignedProducts = new HashSet<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 30)
    @Builder.Default
    private List<CustomerAddress> addresses = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TaxType       { STANDARD, EXEMPT, ZERO_RATED }
    public enum VisibilityRule { ALL, ASSIGNED }
    public enum CustomerStatus { ACTIVE, INACTIVE, SUSPENDED }
    public enum CustomerSource { MANUAL, POS }
}
