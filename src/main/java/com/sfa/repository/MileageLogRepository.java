package com.sfa.repository;

import com.sfa.entity.MileageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MileageLogRepository extends JpaRepository<MileageLog, UUID> {

    /** The session a user is currently "clocked into" — an open session has no end
     *  mileage yet. At most one can exist per user (see idx_mileage_logs_one_open). */
    Optional<MileageLog> findByUserIdAndEndMileageIsNull(UUID userId);

    /** This user's most recently closed session — its end mileage is shown as a
     *  reference on the start-mileage screen when starting a new session. */
    Optional<MileageLog> findFirstByUserIdAndEndMileageIsNotNullOrderByEndedAtDesc(UUID userId);

    @Query("""
        SELECT m FROM MileageLog m LEFT JOIN FETCH m.user
        WHERE (:userId IS NULL OR m.user.id = :userId)
          AND m.logDate >= COALESCE(:dateFrom, m.logDate)
          AND m.logDate <= COALESCE(:dateTo, m.logDate)
        ORDER BY m.logDate DESC
        """)
    Page<MileageLog> findLogs(
            @Param("userId") UUID userId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);
}
