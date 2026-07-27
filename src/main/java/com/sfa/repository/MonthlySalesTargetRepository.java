package com.sfa.repository;

import com.sfa.entity.MonthlySalesTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MonthlySalesTargetRepository extends JpaRepository<MonthlySalesTarget, UUID> {

    boolean existsBySalesRepIdAndProductIdAndTargetYearAndTargetMonth(
            UUID salesRepId, UUID productId, int targetYear, int targetMonth);

    List<MonthlySalesTarget> findBySalesRepIdAndTargetYearAndTargetMonth(
            UUID salesRepId, int targetYear, int targetMonth);

    @Query("""
        SELECT t FROM MonthlySalesTarget t
        LEFT JOIN FETCH t.salesRep
        LEFT JOIN FETCH t.product
        WHERE (:repId IS NULL OR t.salesRep.id = :repId)
          AND (:year IS NULL OR t.targetYear = :year)
          AND (:month IS NULL OR t.targetMonth = :month)
        ORDER BY t.targetYear DESC, t.targetMonth DESC, t.createdAt DESC
        """)
    Page<MonthlySalesTarget> findFiltered(
            @Param("repId") UUID repId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            Pageable pageable);
}
