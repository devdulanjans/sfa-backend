package com.sfa.repository;

import com.sfa.entity.StockBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM StockBatch b
        WHERE b.productId = :productId AND b.remainingQty > 0
        ORDER BY b.receivedDate ASC, b.createdAt ASC
        """)
    List<StockBatch> findAvailableForUpdate(@Param("productId") UUID productId);

    @Query("SELECT b FROM StockBatch b WHERE b.productId = :productId ORDER BY b.receivedDate DESC, b.createdAt DESC")
    List<StockBatch> findAllForProduct(@Param("productId") UUID productId);
}
