package com.sfa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.sfa.security.TenantAwareEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_prices", indexes = {
    @Index(name = "idx_bp_product_date",  columnList = "product_id,start_date"),
    @Index(name = "idx_bp_customer",      columnList = "customer_id"),
    @Index(name = "idx_bp_product_cust",  columnList = "product_id,customer_id"),
    @Index(name = "idx_bp_tenant",        columnList = "tenant_id")
})
@EntityListeners({AuditingEntityListener.class, TenantAwareEntityListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BatchPrice implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // This whole entity is returned directly (not via a DTO) from BatchPriceController's
    // list/sync endpoints — @JsonIgnore keeps the tenant relation out of that response
    // entirely rather than risk Jackson trying to serialize a lazy proxy.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_group_id")
    private CustomerGroup customerGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id")
    private ProductGroup productGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "min_qty", precision = 10, scale = 3)
    private BigDecimal minQty;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
