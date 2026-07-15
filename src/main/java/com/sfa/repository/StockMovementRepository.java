package com.sfa.repository;

import com.sfa.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Optional<StockMovement> findByReferenceTypeAndReferenceIdAndProductIdAndType(
            String referenceType, UUID referenceId, UUID productId, StockMovement.MovementType type);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN m.type = 'POS_OUT' THEN m.totalCost ELSE -m.totalCost END), 0)
        FROM StockMovement m
        WHERE m.type IN ('POS_OUT','POS_VOID_IN') AND m.totalCost IS NOT NULL
          AND m.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumCogsBetween(@Param("from") Instant from, @Param("to") Instant to);

    // All
    @Query("SELECT m FROM StockMovement m ORDER BY m.createdAt DESC")
    Page<StockMovement> findAllOrdered(Pageable pageable);

    // Filter by product only
    @Query("SELECT m FROM StockMovement m WHERE m.productId = :productId ORDER BY m.createdAt DESC")
    Page<StockMovement> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    // Filter by type only
    @Query("SELECT m FROM StockMovement m WHERE m.type = :type ORDER BY m.createdAt DESC")
    Page<StockMovement> findByType(@Param("type") StockMovement.MovementType type, Pageable pageable);

    // Filter by both
    @Query("SELECT m FROM StockMovement m WHERE m.productId = :productId AND m.type = :type ORDER BY m.createdAt DESC")
    Page<StockMovement> findByProductIdAndType(
            @Param("productId") UUID productId,
            @Param("type") StockMovement.MovementType type,
            Pageable pageable);
}
