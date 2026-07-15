package com.sfa.repository;

import com.sfa.entity.PosSale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {

    @Query("""
        SELECT s FROM PosSale s LEFT JOIN FETCH s.customer
        WHERE (:status IS NULL OR s.status = :status)
        ORDER BY s.createdAt DESC
        """)
    Page<PosSale> findFiltered(
            @Param("status") PosSale.SaleStatus status,
            Pageable pageable);

    @Query("SELECT s FROM PosSale s LEFT JOIN FETCH s.items WHERE s.id = :id")
    Optional<PosSale> findByIdWithItems(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PosSale s WHERE s.id = :id")
    Optional<PosSale> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT s FROM PosSale s LEFT JOIN FETCH s.customer
        WHERE s.paymentMethod = 'CREDIT' AND s.status = 'COMPLETED'
          AND (:customerId IS NULL OR s.customer.id = :customerId)
          AND (:creditStatus IS NULL OR s.creditStatus = :creditStatus)
          AND s.createdAt >= COALESCE(:dateFrom, s.createdAt)
          AND s.createdAt <= COALESCE(:dateTo, s.createdAt)
        ORDER BY s.createdAt DESC
        """)
    Page<PosSale> findCreditBills(
            @Param("customerId") UUID customerId,
            @Param("creditStatus") PosSale.CreditStatus creditStatus,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(s.balanceDue), 0) FROM PosSale s
        WHERE s.paymentMethod = 'CREDIT' AND s.status = 'COMPLETED'
          AND (:customerId IS NULL OR s.customer.id = :customerId)
          AND (:creditStatus IS NULL OR s.creditStatus = :creditStatus)
          AND s.createdAt >= COALESCE(:dateFrom, s.createdAt)
          AND s.createdAt <= COALESCE(:dateTo, s.createdAt)
        """)
    BigDecimal sumBalanceDueForCreditBills(
            @Param("customerId") UUID customerId,
            @Param("creditStatus") PosSale.CreditStatus creditStatus,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo);

    @Query("""
        SELECT s FROM PosSale s
        WHERE s.customer.id = :customerId AND s.paymentMethod = 'CREDIT' AND s.status = 'COMPLETED'
          AND s.creditStatus <> 'PAID'
        ORDER BY s.createdAt ASC
        """)
    List<PosSale> findOpenCreditSalesForCustomer(@Param("customerId") UUID customerId);

    // ── Cash drawer ──────────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(s.total), 0) FROM PosSale s
        WHERE s.status = 'COMPLETED' AND s.paymentMethod = 'CASH'
          AND s.createdBy = :cashierId AND s.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumCashSalesForCashier(@Param("cashierId") UUID cashierId,
                                       @Param("from") Instant from, @Param("to") Instant to);

    // ── Dashboard aggregates ─────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM PosSale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(s) FROM PosSale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to")
    long countCompletedBetween(@Param("from") Instant from, @Param("to") Instant to);

    List<PosSale> findTop10ByStatusOrderByCreatedAtDesc(PosSale.SaleStatus status);

    @Query(value = """
        SELECT TO_CHAR(DATE_TRUNC('day', s.created_at AT TIME ZONE 'UTC'), 'YYYY-MM-DD') AS date,
               COALESCE(SUM(s.total), 0) AS revenue,
               COUNT(s.id) AS sale_count
        FROM pos_sales s
        WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC('day', s.created_at AT TIME ZONE 'UTC')
        ORDER BY DATE_TRUNC('day', s.created_at AT TIME ZONE 'UTC')
        """, nativeQuery = true)
    List<Object[]> dailyRevenueRaw(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT COALESCE(i.product_name, 'Unknown') AS product_name,
               SUM(i.quantity) AS qty,
               SUM(i.line_total) AS revenue
        FROM pos_sale_items i
        JOIN pos_sales s ON s.id = i.sale_id
        WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to
        GROUP BY i.product_name
        ORDER BY qty DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topProductsRaw(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    @Query(value = """
        SELECT (s.customer_id IS NULL) AS is_walkin,
               COALESCE(SUM(s.total), 0) AS revenue,
               COUNT(s.id) AS sale_count
        FROM pos_sales s
        WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to
        GROUP BY (s.customer_id IS NULL)
        """, nativeQuery = true)
    List<Object[]> walkInVsRegisteredRaw(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT c.name, COALESCE(SUM(s.total), 0) AS revenue, COUNT(s.id) AS sale_count
        FROM pos_sales s
        JOIN customers c ON c.id = s.customer_id
        WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to
        GROUP BY c.id, c.name
        ORDER BY revenue DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topCustomersRaw(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    @Query(value = """
        SELECT s.payment_method, COALESCE(SUM(s.total), 0) AS revenue, COUNT(s.id) AS sale_count
        FROM pos_sales s
        WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to
        GROUP BY s.payment_method
        """, nativeQuery = true)
    List<Object[]> paymentMethodBreakdownRaw(@Param("from") Instant from, @Param("to") Instant to);

    // ── Reports ──────────────────────────────────────────────────────────────

    @Query(
        value = """
            SELECT s.id, s.sale_number, s.created_at, u.full_name AS cashier_name,
                   COALESCE(s.customer_name, 'Walk-in') AS customer_name,
                   s.payment_method, s.subtotal, s.discount_amount, s.tax_amount, s.total,
                   s.credit_status, s.status
            FROM pos_sales s
            LEFT JOIN users u ON u.id = s.created_by
            WHERE s.status = 'COMPLETED'
              AND (:cashierId IS NULL OR s.created_by = :cashierId)
              AND (:customerId IS NULL OR s.customer_id = :customerId)
              AND s.created_at >= COALESCE(:dateFrom, s.created_at)
              AND s.created_at <= COALESCE(:dateTo, s.created_at)
              AND (:productId IS NULL OR EXISTS (
                    SELECT 1 FROM pos_sale_items i WHERE i.sale_id = s.id AND i.product_id = :productId))
            ORDER BY s.created_at DESC
            """,
        countQuery = """
            SELECT COUNT(s.id)
            FROM pos_sales s
            WHERE s.status = 'COMPLETED'
              AND (:cashierId IS NULL OR s.created_by = :cashierId)
              AND (:customerId IS NULL OR s.customer_id = :customerId)
              AND s.created_at >= COALESCE(:dateFrom, s.created_at)
              AND s.created_at <= COALESCE(:dateTo, s.created_at)
              AND (:productId IS NULL OR EXISTS (
                    SELECT 1 FROM pos_sale_items i WHERE i.sale_id = s.id AND i.product_id = :productId))
            """,
        nativeQuery = true
    )
    Page<Object[]> findReportRaw(
            @Param("cashierId") UUID cashierId,
            @Param("customerId") UUID customerId,
            @Param("productId") UUID productId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query(value = """
        SELECT DISTINCT u.id, u.full_name
        FROM pos_sales s
        JOIN users u ON u.id = s.created_by
        ORDER BY u.full_name
        """, nativeQuery = true)
    List<Object[]> findCashiersRaw();

    // ── Daily report ─────────────────────────────────────────────────────────

    @Query(value = """
        SELECT s.id, s.sale_number, s.created_at, u.full_name AS cashier_name,
               COALESCE(s.customer_name, 'Walk-in') AS customer_name,
               s.payment_method, s.card_last4, s.subtotal, s.discount_amount, s.tax_amount, s.total,
               s.amount_paid, s.balance_due, s.credit_status, s.status
        FROM pos_sales s
        LEFT JOIN users u ON u.id = s.created_by
        WHERE s.status = 'COMPLETED'
          AND (:cashierId IS NULL OR s.created_by = :cashierId)
          AND s.created_at >= :dateFrom AND s.created_at < :dateTo
        ORDER BY s.created_at ASC
        """, nativeQuery = true)
    List<Object[]> findDailyTransactionsRaw(@Param("cashierId") UUID cashierId,
                                             @Param("dateFrom") Instant dateFrom, @Param("dateTo") Instant dateTo);
}
