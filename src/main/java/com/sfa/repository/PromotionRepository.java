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

    boolean existsByCustomerGroupId(UUID customerGroupId);
    boolean existsByProductGroupId(UUID productGroupId);

    /** Returns promotions for this product ordered by priority (customer-specific first).
     *  Returns a List so multiple matches don't throw IncorrectResultSizeDataAccessException.
     *  A product matches via the explicit products list OR current membership in the promotion's
     *  linked product group (additive — see V67 migration); a customer matches via the singular
     *  customer FK OR current membership in the linked customer group, OR both are unset ("all
     *  customers"). */
    @Query("""
        SELECT p FROM Promotion p
        LEFT JOIN p.customer c
        WHERE p.isActive = true
          AND :today BETWEEN p.startDate AND p.endDate
          AND (:productId IN (SELECT prod.id FROM p.products prod)
               OR (p.productGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM ProductGroup pg JOIN pg.members pm
                   WHERE pg = p.productGroup AND pm.id = :productId)))
          AND ((p.customer IS NULL AND p.customerGroup IS NULL)
               OR c.id = :customerId
               OR (p.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = p.customerGroup AND cm.id = :customerId)))
        ORDER BY
          CASE WHEN c.id = :customerId THEN 0
               WHEN p.customerGroup IS NOT NULL THEN 1
               ELSE 2 END
    """)
    List<Promotion> findActivePromotions(UUID productId, UUID customerId, LocalDate today);

    /** All active promotions for a customer: their specific ones, via their customer group, + general. */
    @Query("""
        SELECT DISTINCT p FROM Promotion p
        LEFT JOIN FETCH p.products
        LEFT JOIN p.customer c
        WHERE p.isActive = true
          AND :today BETWEEN p.startDate AND p.endDate
          AND ((p.customer IS NULL AND p.customerGroup IS NULL)
               OR c.id = :customerId
               OR (p.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = p.customerGroup AND cm.id = :customerId)))
        ORDER BY p.name
    """)
    List<Promotion> findActiveForCustomer(
            @Param("customerId") UUID customerId,
            @Param("today") LocalDate today);

    Page<Promotion> findAll(Pageable pageable);

    Page<Promotion> findByIsActiveTrue(Pageable pageable);
}
