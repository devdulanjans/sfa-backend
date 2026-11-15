package com.sfa.repository;

import com.sfa.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    // Codes are unique per channel (V118); an unscoped caller can see the same code twice.
    boolean existsByProductCode(String productCode);
    boolean existsByTenantIdAndProductCode(UUID tenantId, String productCode);
    Optional<Product> findFirstByBarcode(String barcode);
    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    // Used by bulk channel-reassignment to pre-check the per-tenant product_code
    // uniqueness constraint before moving a row into a different tenant.
    boolean existsByTenantIdAndProductCodeAndIdNot(UUID tenantId, String productCode, UUID id);

    List<Product> findByStatusOrderByName(Product.ProductStatus status);

    @Query("""
        SELECT p FROM Customer c JOIN c.assignedProducts p
        WHERE c.id = :customerId AND p.status = 'ACTIVE'
        ORDER BY p.name
    """)
    List<Product> findAssignedToCustomer(@Param("customerId") UUID customerId);

    @Query("""
        SELECT DISTINCT p FROM CustomerGroup cg
        JOIN cg.members m
        JOIN cg.assignedProducts p
        WHERE m.id = :customerId AND p.status = 'ACTIVE'
        ORDER BY p.name
    """)
    List<Product> findAssignedToCustomerViaGroups(@Param("customerId") UUID customerId);

    @Query("""
        SELECT p FROM Product p WHERE
        LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(p.productCode) LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(p.barcode) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<Product> search(String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.updatedAt >= :since")
    List<Product> findUpdatedSince(Instant since);

    /**
     * Public website catalogue: every ACTIVE product whose home channel is ACTIVE, optionally
     * narrowed by a name/code search ({@code query} "" = all) and one channel's code
     * ({@code tenantCode} "" = all). Called from an anonymous request, where the tenant filter
     * is NOT enabled, so the status checks here are what keep inactive rows off the site.
     */
    @Query("""
        SELECT p FROM Product p JOIN p.tenant t
        WHERE p.status = com.sfa.entity.Product$ProductStatus.ACTIVE
          AND t.status = com.sfa.entity.Tenant$TenantStatus.ACTIVE
          AND (:query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:tenantCode = '' OR t.code = :tenantCode)
        """)
    Page<Product> findPublicCatalog(@Param("query") String query, @Param("tenantCode") String tenantCode,
                                    Pageable pageable);
}
