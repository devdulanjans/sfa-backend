package com.sfa.repository;

import com.sfa.entity.SalesRouteAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesRouteAssignmentRepository extends JpaRepository<SalesRouteAssignment, UUID> {

    /** Assignments in a date range, optionally for one rep / one route. Channel-scoped by the tenant filter. */
    @Query("""
        SELECT a FROM SalesRouteAssignment a
        JOIN FETCH a.route r
        JOIN FETCH a.salesRep s
        LEFT JOIN FETCH a.assignedBy
        WHERE a.assignmentDate BETWEEN :from AND :to
          AND (:salesRepId IS NULL OR s.id = :salesRepId)
          AND (:routeId IS NULL OR r.id = :routeId)
        ORDER BY a.assignmentDate, s.fullName
    """)
    List<SalesRouteAssignment> findInRange(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("salesRepId") UUID salesRepId,
                                           @Param("routeId") UUID routeId);

    Optional<SalesRouteAssignment> findBySalesRepIdAndAssignmentDate(UUID salesRepId, LocalDate date);

    boolean existsByRouteId(UUID routeId);
}
