package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Channel-specific (V117): each business sees and uses only its own product groups. */
@Entity
@Table(name = "product_groups", uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_groups_tenant_name", columnNames = {"tenant_id", "name"})
})
@EntityListeners({AuditingEntityListener.class, com.sfa.security.TenantAwareEntityListener.class})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id in (:tenantIds)")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductGroup implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_group_members",
        joinColumns = @JoinColumn(name = "product_group_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private Set<Product> members = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
