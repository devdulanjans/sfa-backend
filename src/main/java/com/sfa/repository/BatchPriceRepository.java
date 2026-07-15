package com.sfa.repository;

import com.sfa.entity.BatchPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchPriceRepository extends JpaRepository<BatchPrice, UUID> {

    /**
     * Best customer-specific tier for the given quantity — the qualifying row (minQty null or
     * &lt;= qty) with the highest minQty wins (a null minQty is treated as the qty-1 baseline
     * tier, so it only wins when no higher tier is met), tie-broken by most recent startDate.
     * Used by {@link com.sfa.service.PricingEngine#resolve} so multi-tier customer batch
     * pricing (e.g. qty&gt;=10 vs qty&gt;=20) picks the tier that actually matches the quantity
     * being priced, instead of an arbitrary row.
     */
    @Query("""
        SELECT bp FROM BatchPrice bp
        WHERE bp.product.id = :productId
          AND bp.customer.id = :customerId
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
          AND (bp.minQty IS NULL OR bp.minQty <= :qty)
        ORDER BY COALESCE(bp.minQty, 0) DESC, bp.startDate DESC
        LIMIT 1
    """)
    Optional<BatchPrice> findBestCustomerBatchPrice(UUID productId, UUID customerId, BigDecimal qty, LocalDate date);

    /** General-tier equivalent of {@link #findBestCustomerBatchPrice}. */
    @Query("""
        SELECT bp FROM BatchPrice bp
        WHERE bp.product.id = :productId
          AND bp.customer IS NULL
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
          AND (bp.minQty IS NULL OR bp.minQty <= :qty)
        ORDER BY COALESCE(bp.minQty, 0) DESC, bp.startDate DESC
        LIMIT 1
    """)
    Optional<BatchPrice> findBestGeneralBatchPrice(UUID productId, BigDecimal qty, LocalDate date);

    /** All active tiers for a product visible to a customer.
     *  LEFT JOIN FETCH ensures customer is accessible after the session closes.
     *  Caller sorts: customer-specific first, then by minQty ascending. */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN FETCH bp.customer
        WHERE bp.product.id = :productId
          AND (bp.customer IS NULL OR bp.customer.id = :customerId)
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
    """)
    List<BatchPrice> findAllActiveForProduct(
            @Param("productId") UUID productId,
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);

    /**
     * All active customer-specific batch prices for a given customer across all products.
     * Used by mobile to pre-load the effective price map before browsing — note this is
     * qty-agnostic (returns every active row per product, most-recent startDate first), so
     * {@link com.sfa.controller.PricingController#customerOverrides} must filter out products
     * with more than one active tier before treating a row as a flat "override" price.
     */
    @Query("""
        SELECT bp FROM BatchPrice bp
        JOIN FETCH bp.product
        WHERE bp.customer.id = :customerId
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
        ORDER BY bp.startDate DESC
    """)
    List<BatchPrice> findAllActiveForCustomer(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);

    /**
     * Product IDs with ANY active batch price visible to this customer (general
     * tiers, or tiers specific to this customer). Used by mobile to bulk-check
     * which products in a list have batch pricing, so it can hide the plain
     * default price for them (the real price depends on the qty tier picked).
     */
    @Query("""
        SELECT DISTINCT bp.product.id FROM BatchPrice bp
        WHERE (bp.customer IS NULL OR bp.customer.id = :customerId)
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
    """)
    List<UUID> findActiveProductIdsVisibleToCustomer(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);
}
