package com.sfa.repository;

import com.sfa.entity.AccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLog, UUID> {

    @Query("""
        SELECT l FROM AccessLog l
        WHERE (:userId IS NULL OR l.userId = :userId)
          AND (:status IS NULL OR l.status = :status)
          AND (:action IS NULL OR l.action = :action)
        ORDER BY l.createdAt DESC
        """)
    Page<AccessLog> findFiltered(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("action") String action,
            Pageable pageable);
}
