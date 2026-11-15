package com.sfa.repository;

import com.sfa.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    boolean existsByOrderId(UUID orderId);
    /** An order can have several invoices (V120) — the most recent one. */
    Optional<Invoice> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
    List<Invoice> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    List<Invoice> findByOrderIdIn(Collection<UUID> orderIds);

    /** A customer's unpaid invoices, oldest due first — where a received payment is applied. */
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.customer.id = :customerId AND i.status IN :statuses AND i.total > i.paidAmount
        ORDER BY COALESCE(i.dueDate, i.issuedDate) ASC, i.issuedDate ASC, i.createdAt ASC
        """)
    List<Invoice> findPayableForCustomer(@Param("customerId") UUID customerId,
                                         @Param("statuses") List<Invoice.InvoiceStatus> statuses);
    Page<Invoice> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Invoice> findByIssuedDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    @Query(
        value      = "SELECT i FROM Invoice i JOIN FETCH i.customer JOIN FETCH i.order o JOIN FETCH o.salesRep ORDER BY i.issuedDate DESC, i.createdAt DESC",
        countQuery = "SELECT COUNT(i) FROM Invoice i"
    )
    Page<Invoice> findAllWithCustomer(Pageable pageable);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status AND i.createdAt BETWEEN :from AND :to")
    long countIssuedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") Invoice.InvoiceStatus status);

    default long countIssuedBetween(Instant from, Instant to) {
        return countIssuedBetween(from, to, Invoice.InvoiceStatus.ISSUED);
    }

    @Query("""
        SELECT i.customer.id, i.customer.name, SUM(i.total - i.paidAmount)
        FROM Invoice i
        WHERE i.status IN :statuses
        GROUP BY i.customer.id, i.customer.name
        HAVING SUM(i.total - i.paidAmount) > 0
        ORDER BY SUM(i.total - i.paidAmount) DESC
        """)
    List<Object[]> findOutstandingByCustomer(@Param("statuses") List<Invoice.InvoiceStatus> statuses);

    /**
     * Outstanding balance per customer with ageing (V120): customer id, name, code, phone,
     * invoice count, balance, overdue balance, oldest due date. Channel-filtered (HQL).
     */
    @Query("""
        SELECT i.customer.id, i.customer.name, i.customer.customerCode, i.customer.phone,
               COUNT(i), SUM(i.total - i.paidAmount),
               SUM(CASE WHEN i.dueDate < :today THEN i.total - i.paidAmount ELSE 0 END),
               MIN(i.dueDate)
        FROM Invoice i
        WHERE i.status IN :statuses AND i.total > i.paidAmount
        GROUP BY i.customer.id, i.customer.name, i.customer.customerCode, i.customer.phone
        ORDER BY SUM(i.total - i.paidAmount) DESC
        """)
    List<Object[]> balancesByCustomer(@Param("statuses") List<Invoice.InvoiceStatus> statuses,
                                      @Param("today") LocalDate today);

    @Query("""
        SELECT i FROM Invoice i JOIN FETCH i.customer
        WHERE i.customer.id = :customerId AND i.status IN :statuses
        ORDER BY i.issuedDate DESC
        """)
    List<Invoice> findOutstandingForCustomer(
            @Param("customerId") UUID customerId,
            @Param("statuses") List<Invoice.InvoiceStatus> statuses);
}
