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
    Optional<Product> findByProductCode(String productCode);
    Optional<Product> findByBarcode(String barcode);
    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    List<Product> findByStatusOrderByName(Product.ProductStatus status);

    @Query("""
        SELECT p FROM Customer c JOIN c.assignedProducts p
        WHERE c.id = :customerId AND p.status = 'ACTIVE'
        ORDER BY p.name
    """)
    List<Product> findAssignedToCustomer(@Param("customerId") UUID customerId);

    @Query("""
        SELECT p FROM Product p WHERE
        LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(p.productCode) LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(p.barcode) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<Product> search(String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.updatedAt >= :since")
    List<Product> findUpdatedSince(Instant since);
}
