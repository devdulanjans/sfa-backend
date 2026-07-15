package com.sfa.repository;

import com.sfa.dto.report.CustomerSalesDto;
import com.sfa.dto.report.SalesSummaryDto;
import com.sfa.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Page<Order> findBySalesRepId(UUID salesRepId, Pageable pageable);
    Page<Order> findBySalesRepIdAndStatus(UUID salesRepId, Order.OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(Order.OrderStatus status, Pageable pageable);
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Order> findByCustomerIdAndStatus(UUID customerId, Order.OrderStatus status, Pageable pageable);

    Page<Order> findByOrderSource(Order.OrderSource source, Pageable pageable);
    Page<Order> findByOrderSourceAndStatus(Order.OrderSource source, Order.OrderStatus status, Pageable pageable);

    // ── Report queries ────────────────────────────────────────────────────────
    // Hibernate 6 no longer coerces string literals to enum values in JPQL.
    // All status comparisons pass proper enum constants via @Param.

    List<Order.OrderStatus> EXCLUDED_STATUSES =
            List.of(Order.OrderStatus.DRAFT, Order.OrderStatus.CANCELLED);

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.salesRep.id = :repId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    long countBetweenByRep_(
            @Param("repId") UUID repId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default long countBetweenByRep(UUID repId, Instant from, Instant to) {
        return countBetweenByRep_(repId, from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT o FROM Order o
        WHERE o.salesRep.id = :repId
          AND o.orderDate BETWEEN :from AND :to
        ORDER BY o.orderDate DESC
    """)
    List<Order> findByRepAndDateRange(
            @Param("repId") UUID repId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        SELECT SUM(o.total) FROM Order o
        WHERE o.salesRep.id = :repId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    BigDecimal sumRevenueByRep_(
            @Param("repId") UUID repId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default BigDecimal sumRevenueByRep(UUID repId, Instant from, Instant to) {
        return sumRevenueByRep_(repId, from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT SUM(o.total) FROM Order o
        WHERE o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    BigDecimal totalRevenueBetween_(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default BigDecimal totalRevenueBetween(Instant from, Instant to) {
        return totalRevenueBetween_(from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    long countBetween_(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default long countBetween(Instant from, Instant to) {
        return countBetween_(from, to, EXCLUDED_STATUSES);
    }

    @Query(value = """
        SELECT TO_CHAR(DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC'), 'YYYY-MM-DD') AS date,
               COALESCE(SUM(o.total), 0)                                                  AS revenue
        FROM orders o
        WHERE o.order_date BETWEEN :from AND :to
          AND o.status NOT IN ('DRAFT','CANCELLED')
        GROUP BY DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC')
        ORDER BY DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC')
    """, nativeQuery = true)
    List<Object[]> dailyRevenueRaw(@Param("from") Instant from, @Param("to") Instant to);

    default List<SalesSummaryDto.DailyRevenueDto> dailyRevenueBetween(Instant from, Instant to) {
        return dailyRevenueRaw(from, to).stream()
                .map(row -> new SalesSummaryDto.DailyRevenueDto(
                        String.valueOf(row[0]),
                        row[1] instanceof BigDecimal bd ? bd
                                : new BigDecimal(row[1].toString())))
                .toList();
    }

    @Query(
        value = """
            SELECT new com.sfa.dto.report.CustomerSalesDto(
                o.customer.id,
                o.customer.name,
                SUM(o.total),
                COUNT(o)
            )
            FROM Order o
            WHERE o.orderDate BETWEEN :from AND :to
              AND o.status NOT IN :excluded
            GROUP BY o.customer.id, o.customer.name
            ORDER BY SUM(o.total) DESC
        """,
        countQuery = """
            SELECT COUNT(DISTINCT o.customer.id)
            FROM Order o
            WHERE o.orderDate BETWEEN :from AND :to
              AND o.status NOT IN :excluded
        """
    )
    Page<CustomerSalesDto> customerSalesBetween_(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded,
            Pageable pageable);

    default Page<CustomerSalesDto> customerSalesBetween(Instant from, Instant to, Pageable pageable) {
        return customerSalesBetween_(from, to, EXCLUDED_STATUSES, pageable);
    }

    // ── Distributor-filtered variants ─────────────────────────────────────────

    @Query("""
        SELECT SUM(o.total) FROM Order o
        WHERE o.salesRep.id = :repId
          AND o.distributor.id = :distributorId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    BigDecimal sumRevenueByRepAndDistributor_(
            @Param("repId") UUID repId,
            @Param("distributorId") UUID distributorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default BigDecimal sumRevenueByRepAndDistributor(UUID repId, UUID distributorId, Instant from, Instant to) {
        return sumRevenueByRepAndDistributor_(repId, distributorId, from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT SUM(o.total) FROM Order o
        WHERE o.distributor.id = :distributorId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    BigDecimal totalRevenueBetweenAndDistributor_(
            @Param("distributorId") UUID distributorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default BigDecimal totalRevenueBetweenAndDistributor(UUID distributorId, Instant from, Instant to) {
        return totalRevenueBetweenAndDistributor_(distributorId, from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.salesRep.id = :repId
          AND o.distributor.id = :distributorId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    long countBetweenByRepAndDistributor_(
            @Param("repId") UUID repId,
            @Param("distributorId") UUID distributorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default long countBetweenByRepAndDistributor(UUID repId, UUID distributorId, Instant from, Instant to) {
        return countBetweenByRepAndDistributor_(repId, distributorId, from, to, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.distributor.id = :distributorId
          AND o.orderDate BETWEEN :from AND :to
          AND o.status NOT IN :excluded
    """)
    long countBetweenAndDistributor_(
            @Param("distributorId") UUID distributorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluded") Collection<Order.OrderStatus> excluded);

    default long countBetweenAndDistributor(UUID distributorId, Instant from, Instant to) {
        return countBetweenAndDistributor_(distributorId, from, to, EXCLUDED_STATUSES);
    }

    // ── Customer analytics ────────────────────────────────────────────────────

    Page<Order> findByCustomerIdOrderByOrderDateDesc(UUID customerId, Pageable pageable);

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.customer.id = :customerId
          AND o.status NOT IN :excluded
    """)
    long countByCustomer_(@Param("customerId") UUID customerId,
                           @Param("excluded") Collection<Order.OrderStatus> excluded);

    default long countByCustomer(UUID customerId) {
        return countByCustomer_(customerId, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT SUM(o.total) FROM Order o
        WHERE o.customer.id = :customerId
          AND o.status NOT IN :excluded
    """)
    BigDecimal sumTotalByCustomer_(@Param("customerId") UUID customerId,
                                    @Param("excluded") Collection<Order.OrderStatus> excluded);

    default BigDecimal sumTotalByCustomer(UUID customerId) {
        return sumTotalByCustomer_(customerId, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT MAX(o.orderDate) FROM Order o
        WHERE o.customer.id = :customerId
          AND o.status NOT IN :excluded
    """)
    Optional<Instant> lastOrderDateByCustomer_(@Param("customerId") UUID customerId,
                                                @Param("excluded") Collection<Order.OrderStatus> excluded);

    default Optional<Instant> lastOrderDateByCustomer(UUID customerId) {
        return lastOrderDateByCustomer_(customerId, EXCLUDED_STATUSES);
    }

    @Query("""
        SELECT o.status, COUNT(o) FROM Order o
        WHERE o.customer.id = :customerId
        GROUP BY o.status
    """)
    List<Object[]> statusBreakdownByCustomer(@Param("customerId") UUID customerId);

    @Query("""
        SELECT oi.product.productCode, oi.product.name, oi.product.unit.name,
               SUM(oi.quantity), SUM(oi.lineTotal)
        FROM OrderItem oi
        WHERE oi.order.customer.id = :customerId
          AND oi.order.status NOT IN :excluded
          AND oi.order.orderDate >= :since
        GROUP BY oi.product.id, oi.product.productCode, oi.product.name, oi.product.unit.name
        ORDER BY SUM(oi.quantity) DESC
    """)
    List<Object[]> customerTopProducts_(@Param("customerId") UUID customerId,
                                         @Param("since") Instant since,
                                         @Param("excluded") Collection<Order.OrderStatus> excluded,
                                         Pageable pageable);

    default List<Object[]> customerTopProducts(UUID customerId, Instant since) {
        return customerTopProducts_(customerId, since, EXCLUDED_STATUSES, PageRequest.of(0, 10));
    }

    @Query(value = """
        SELECT TO_CHAR(DATE_TRUNC('month', o.order_date AT TIME ZONE 'UTC'), 'YYYY-MM') AS month,
               COALESCE(SUM(o.total), 0)                                                  AS revenue,
               COUNT(*)                                                                    AS order_count
        FROM orders o
        WHERE o.customer_id = :customerId
          AND o.status NOT IN ('DRAFT','CANCELLED')
          AND o.order_date >= :since
        GROUP BY DATE_TRUNC('month', o.order_date AT TIME ZONE 'UTC')
        ORDER BY 1
    """, nativeQuery = true)
    List<Object[]> customerMonthlyRevenue(@Param("customerId") UUID customerId,
                                           @Param("since") Instant since);

    // ── Dashboard chart queries ───────────────────────────────────────────────

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.orderDate BETWEEN :from AND :to GROUP BY o.status")
    List<Object[]> statusBreakdownBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.salesRep.id = :repId AND o.orderDate BETWEEN :from AND :to GROUP BY o.status")
    List<Object[]> statusBreakdownByRepBetween(
            @Param("repId") UUID repId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT TO_CHAR(DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC'), 'YYYY-MM-DD'),
               COALESCE(SUM(o.total), 0)
        FROM orders o
        WHERE o.sales_rep_id = :repId
          AND o.order_date BETWEEN :from AND :to
          AND o.status NOT IN ('DRAFT','CANCELLED')
        GROUP BY DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC')
        ORDER BY DATE_TRUNC('day', o.order_date AT TIME ZONE 'UTC')
    """, nativeQuery = true)
    List<Object[]> dailyRevenueByRepRaw(
            @Param("repId") UUID repId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT c.name, COALESCE(SUM(o.total), 0), COUNT(o.id)
        FROM orders o
        JOIN customers c ON c.id = o.customer_id
        WHERE o.sales_rep_id = :repId
          AND o.order_date BETWEEN :from AND :to
          AND o.status NOT IN ('DRAFT','CANCELLED')
        GROUP BY c.id, c.name
        ORDER BY 2 DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Object[]> topCustomersByRepRaw(
            @Param("repId") UUID repId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT c.name, COALESCE(SUM(o.total), 0), COUNT(o.id)
        FROM orders o
        JOIN customers c ON c.id = o.customer_id
        WHERE o.order_date BETWEEN :from AND :to
          AND o.status NOT IN ('DRAFT','CANCELLED')
        GROUP BY c.id, c.name
        ORDER BY 2 DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Object[]> topCustomersAllRaw(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT o.order_number, c.name, o.total, o.status,
               TO_CHAR(o.order_date AT TIME ZONE 'UTC', 'YYYY-MM-DD')
        FROM orders o
        JOIN customers c ON c.id = o.customer_id
        WHERE o.sales_rep_id = :repId
        ORDER BY o.order_date DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Object[]> recentOrdersByRepRaw(@Param("repId") UUID repId);

    @Query(value = """
        SELECT o.order_number, c.name, o.total, o.status,
               TO_CHAR(o.order_date AT TIME ZONE 'UTC', 'YYYY-MM-DD')
        FROM orders o
        JOIN customers c ON c.id = o.customer_id
        ORDER BY o.order_date DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Object[]> recentOrdersAllRaw();

    @Query(value = """
        SELECT u.id, u.full_name,
               COALESCE(SUM(o.total), 0)                                AS revenue,
               COUNT(o.id)                                              AS order_count,
               COALESCE(SUM(o.total) / NULLIF(COUNT(o.id), 0), 0)      AS avg_value
        FROM orders o
        JOIN users u ON u.id = o.sales_rep_id
        WHERE o.order_date BETWEEN :from AND :to
          AND o.status NOT IN ('DRAFT','CANCELLED')
        GROUP BY u.id, u.full_name
        ORDER BY revenue DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Object[]> topSalesRepsByRevenue(@Param("from") Instant from, @Param("to") Instant to);
}
