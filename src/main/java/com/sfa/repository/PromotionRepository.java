package com.sfa.repository;

import com.sfa.entity.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    /** Returns promotions for this product ordered by priority (customer-specific first).
     *  Returns a List so multiple matches don't throw IncorrectResultSizeDataAccessException. */
    @Query("""
        SELECT p FROM Promotion p
        JOIN p.products prod
        WHERE p.isActive = true
          AND :today BETWEEN p.startDate AND p.endDate
          AND prod.id = :productId
          AND (p.customer IS NULL OR p.customer.id = :customerId)
        ORDER BY
          CASE WHEN p.customer IS NOT NULL AND p.customer.id = :customerId THEN 0
               ELSE 1 END
    """)
    List<Promotion> findActivePromotions(UUID productId, UUID customerId, LocalDate today);

    /** All active promotions for a customer: their specific ones + general (customer IS NULL). */
    @Query("""
        SELECT DISTINCT p FROM Promotion p
        LEFT JOIN FETCH p.products
        WHERE p.isActive = true
          AND :today BETWEEN p.startDate AND p.endDate
          AND (p.customer IS NULL OR p.customer.id = :customerId)
        ORDER BY p.name
    """)
    List<Promotion> findActiveForCustomer(
            @Param("customerId") UUID customerId,
            @Param("today") LocalDate today);

    Page<Promotion> findAll(Pageable pageable);

    Page<Promotion> findByIsActiveTrue(Pageable pageable);
}
