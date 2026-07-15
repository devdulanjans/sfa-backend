package com.sfa.repository;

import com.sfa.entity.StockBatchConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockBatchConsumptionRepository extends JpaRepository<StockBatchConsumption, UUID> {

    List<StockBatchConsumption> findByMovementId(UUID movementId);
}
