package com.sfa.repository;

import com.sfa.entity.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

    Optional<StockLevel> findByProductId(UUID productId);

    List<StockLevel> findByProductIdIn(List<UUID> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.productId = :productId")
    Optional<StockLevel> findByProductIdForUpdate(@Param("productId") UUID productId);

    @Modifying
    @Query(value = """
        INSERT INTO stock_levels(product_id, on_hand, reserved, updated_at)
        VALUES (:productId, 0, 0, NOW())
        ON CONFLICT (product_id) DO NOTHING
        """, nativeQuery = true)
    void ensureExists(@Param("productId") UUID productId);
}
