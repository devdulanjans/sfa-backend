package com.sfa.repository;

import com.sfa.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM CashMovement m WHERE m.session.id = :sessionId AND m.type = :type")
    BigDecimal sumBySessionAndType(@Param("sessionId") UUID sessionId, @Param("type") CashMovement.Type type);
}
