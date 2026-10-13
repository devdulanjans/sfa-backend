package com.sfa.repository;

import com.sfa.entity.VisitNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitNoteRepository extends JpaRepository<VisitNote, UUID> {

    Optional<VisitNote> findBySalesRepIdAndCustomerIdAndVisitDate(UUID salesRepId, UUID customerId, LocalDate date);

    @Query("""
        SELECT v FROM VisitNote v JOIN FETCH v.customer WHERE v.salesRep.id = :repId AND v.visitDate = :date
    """)
    List<VisitNote> findForRepOnDate(@Param("repId") UUID repId, @Param("date") LocalDate date);

    /** Admin report. Every filter is optional (null = any). Channel-scoped by the tenant filter. */
    @Query(
        value = """
            SELECT v FROM VisitNote v
            JOIN FETCH v.customer c
            JOIN FETCH v.salesRep s
            LEFT JOIN FETCH v.route r
            LEFT JOIN FETCH v.distributor d
            LEFT JOIN FETCH v.tenant t
            WHERE v.visitDate BETWEEN :from AND :to
              AND (:routeId IS NULL OR r.id = :routeId)
              AND (:salesRepId IS NULL OR s.id = :salesRepId)
              AND (:tenantId IS NULL OR t.id = :tenantId)
              AND (:distributorId IS NULL OR d.id = :distributorId)
              AND (:reasonId IS NULL OR v.reason.id = :reasonId)
              AND (:customerId IS NULL OR c.id = :customerId)
            ORDER BY v.visitedAt DESC
        """,
        countQuery = """
            SELECT COUNT(v) FROM VisitNote v
            WHERE v.visitDate BETWEEN :from AND :to
              AND (:routeId IS NULL OR v.route.id = :routeId)
              AND (:salesRepId IS NULL OR v.salesRep.id = :salesRepId)
              AND (:tenantId IS NULL OR v.tenant.id = :tenantId)
              AND (:distributorId IS NULL OR v.distributor.id = :distributorId)
              AND (:reasonId IS NULL OR v.reason.id = :reasonId)
              AND (:customerId IS NULL OR v.customer.id = :customerId)
        """)
    Page<VisitNote> search(@Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           @Param("routeId") UUID routeId,
                           @Param("salesRepId") UUID salesRepId,
                           @Param("tenantId") UUID tenantId,
                           @Param("distributorId") UUID distributorId,
                           @Param("reasonId") UUID reasonId,
                           @Param("customerId") UUID customerId,
                           Pageable pageable);

    /** Count per reason label for the same filters — the report's summary chips. */
    @Query("""
        SELECT v.reasonLabel, COUNT(v) FROM VisitNote v
        WHERE v.visitDate BETWEEN :from AND :to
          AND (:routeId IS NULL OR v.route.id = :routeId)
          AND (:salesRepId IS NULL OR v.salesRep.id = :salesRepId)
          AND (:tenantId IS NULL OR v.tenant.id = :tenantId)
          AND (:distributorId IS NULL OR v.distributor.id = :distributorId)
          AND (:customerId IS NULL OR v.customer.id = :customerId)
        GROUP BY v.reasonLabel ORDER BY COUNT(v) DESC
    """)
    List<Object[]> countByReason(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("routeId") UUID routeId,
                                 @Param("salesRepId") UUID salesRepId,
                                 @Param("tenantId") UUID tenantId,
                                 @Param("distributorId") UUID distributorId,
                                 @Param("customerId") UUID customerId);
}
