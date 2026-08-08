package com.sfa.repository;

import com.sfa.entity.BatchPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchPriceRepository extends JpaRepository<BatchPrice, UUID> {

    boolean existsByCustomerGroupId(UUID customerGroupId);
    boolean existsByProductGroupId(UUID productGroupId);

    /**
     * Admin list-page filtering — each param is optional (null = no restriction on that
     * dimension). Customer/customer-group/product filters match the row's direct target only
     * (not group membership), mirroring the distinct "Customer" vs "Customer Group" columns
     * shown in the admin table. Date range matches any rule whose [startDate, endDate] validity
     * window overlaps the given range. Explicit LEFT JOINs on product/customer avoid the
     * implicit-inner-join pitfall noted on {@link #findBestCustomerBatchPrice} above.
     */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN bp.product p
        LEFT JOIN bp.customer c
        WHERE (:customerId IS NULL OR c.id = :customerId)
          AND (:customerGroupId IS NULL OR bp.customerGroup.id = :customerGroupId)
          AND (:productId IS NULL OR p.id = :productId)
          AND (:startDate IS NULL OR bp.endDate IS NULL OR bp.endDate >= :startDate)
          AND (:endDate IS NULL OR bp.startDate <= :endDate)
    """)
    Page<BatchPrice> findFiltered(
            @Param("customerId") UUID customerId,
            @Param("customerGroupId") UUID customerGroupId,
            @Param("productId") UUID productId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Best customer-targeted tier for the given quantity — matches a row whose customer target
     * (direct customer OR customer group membership) and product target (direct product OR
     * product group membership) both cover the given ids. Direct matches outrank group matches on
     * both sides (see ORDER BY); within the same specificity, the qualifying row (minQty null or
     * &lt;= qty) with the highest minQty wins, tie-broken by most recent startDate. Explicit LEFT
     * JOINs on product/customer are required here — the implicit dot-path {@code bp.product.id}
     * generates an inner join in Hibernate, which would silently exclude product-group/
     * customer-group rows (where the direct FK is null) from ever matching via the OR below.
     */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN bp.product p
        LEFT JOIN bp.customer c
        WHERE (p.id = :productId
               OR (bp.productGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM ProductGroup pg JOIN pg.members pm
                   WHERE pg = bp.productGroup AND pm.id = :productId)))
          AND (c.id = :customerId
               OR (bp.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = bp.customerGroup AND cm.id = :customerId)))
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
          AND (bp.minQty IS NULL OR bp.minQty <= :qty)
        ORDER BY
          CASE WHEN c.id IS NOT NULL THEN 0 ELSE 1 END,
          CASE WHEN p.id IS NOT NULL THEN 0 ELSE 1 END,
          COALESCE(bp.minQty, 0) DESC, bp.startDate DESC
        LIMIT 1
    """)
    Optional<BatchPrice> findBestCustomerBatchPrice(
            @Param("productId") UUID productId, @Param("customerId") UUID customerId,
            @Param("qty") BigDecimal qty, @Param("date") LocalDate date);

    /** General-tier equivalent of {@link #findBestCustomerBatchPrice} — no customer or customer
     *  group targeting at all (applies to every customer), matched against the product side
     *  (direct or group) as above. */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN bp.product p
        WHERE (p.id = :productId
               OR (bp.productGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM ProductGroup pg JOIN pg.members pm
                   WHERE pg = bp.productGroup AND pm.id = :productId)))
          AND bp.customer IS NULL AND bp.customerGroup IS NULL
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
          AND (bp.minQty IS NULL OR bp.minQty <= :qty)
        ORDER BY
          CASE WHEN p.id IS NOT NULL THEN 0 ELSE 1 END,
          COALESCE(bp.minQty, 0) DESC, bp.startDate DESC
        LIMIT 1
    """)
    Optional<BatchPrice> findBestGeneralBatchPrice(
            @Param("productId") UUID productId, @Param("qty") BigDecimal qty, @Param("date") LocalDate date);

    /** All active tiers for a product visible to a customer (direct or group match on both sides).
     *  LEFT JOIN FETCH ensures customer is accessible after the session closes.
     *  Caller sorts: customer-specific first, then by minQty ascending. */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN FETCH bp.customer
        LEFT JOIN bp.product p
        WHERE (p.id = :productId
               OR (bp.productGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM ProductGroup pg JOIN pg.members pm
                   WHERE pg = bp.productGroup AND pm.id = :productId)))
          AND ((bp.customer IS NULL AND bp.customerGroup IS NULL)
               OR bp.customer.id = :customerId
               OR (bp.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = bp.customerGroup AND cm.id = :customerId)))
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
    """)
    List<BatchPrice> findAllActiveForProduct(
            @Param("productId") UUID productId,
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);

    /**
     * All active customer-targeted (direct or via customer group) batch prices for a given
     * customer across all products. Used by mobile to pre-load the effective price map before
     * browsing — note this is qty-agnostic (returns every active row per product, most-recent
     * startDate first), so {@link com.sfa.controller.PricingController#customerOverrides} must
     * filter out rows with no direct product (product-group targeted — not a simple per-product
     * override) and products with more than one active tier before treating a row as a flat
     * "override" price. LEFT JOIN FETCH on product since a row's product may legitimately be null
     * (product-group targeted).
     */
    @Query("""
        SELECT bp FROM BatchPrice bp
        LEFT JOIN FETCH bp.product
        WHERE (bp.customer.id = :customerId
               OR (bp.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = bp.customerGroup AND cm.id = :customerId)))
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
        ORDER BY bp.startDate DESC
    """)
    List<BatchPrice> findAllActiveForCustomer(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);

    /**
     * Direct product IDs (not via a product group) with any active batch price visible to this
     * customer (general tiers, customer-specific, or via customer group). Combined with
     * {@link #findActiveGroupProductIdsVisibleToCustomer} by the caller — kept as two queries
     * rather than one UNION since cross-database JPQL UNION support is inconsistent.
     */
    @Query("""
        SELECT DISTINCT bp.product.id FROM BatchPrice bp
        WHERE bp.product IS NOT NULL
          AND ((bp.customer IS NULL AND bp.customerGroup IS NULL)
               OR bp.customer.id = :customerId
               OR (bp.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = bp.customerGroup AND cm.id = :customerId)))
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
    """)
    List<UUID> findActiveDirectProductIdsVisibleToCustomer(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);

    /** Product IDs reachable via a product-group-targeted batch price visible to this customer —
     *  every current member of any such group. See {@link #findActiveDirectProductIdsVisibleToCustomer}. */
    @Query("""
        SELECT DISTINCT pgm.id FROM BatchPrice bp JOIN bp.productGroup pg JOIN pg.members pgm
        WHERE bp.productGroup IS NOT NULL
          AND ((bp.customer IS NULL AND bp.customerGroup IS NULL)
               OR bp.customer.id = :customerId
               OR (bp.customerGroup IS NOT NULL AND EXISTS (
                   SELECT 1 FROM CustomerGroup cg JOIN cg.members cm
                   WHERE cg = bp.customerGroup AND cm.id = :customerId)))
          AND bp.startDate <= :date
          AND (bp.endDate IS NULL OR bp.endDate >= :date)
    """)
    List<UUID> findActiveGroupProductIdsVisibleToCustomer(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date);
}
