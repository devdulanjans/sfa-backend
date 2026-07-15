package com.sfa.repository;

import com.sfa.entity.DrawerSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DrawerSessionRepository extends JpaRepository<DrawerSession, UUID> {

    boolean existsByCashierIdAndStatus(UUID cashierId, DrawerSession.Status status);

    Optional<DrawerSession> findByCashierIdAndStatus(UUID cashierId, DrawerSession.Status status);

    @Query("""
        SELECT s FROM DrawerSession s LEFT JOIN FETCH s.cashier
        WHERE (:cashierId IS NULL OR s.cashier.id = :cashierId)
          AND (:status IS NULL OR s.status = :status)
          AND s.openedAt >= COALESCE(:dateFrom, s.openedAt)
          AND s.openedAt <= COALESCE(:dateTo, s.openedAt)
        ORDER BY s.openedAt DESC
        """)
    Page<DrawerSession> findSessions(
            @Param("cashierId") UUID cashierId,
            @Param("status") DrawerSession.Status status,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
