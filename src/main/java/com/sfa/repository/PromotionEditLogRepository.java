package com.sfa.repository;

import com.sfa.entity.PromotionEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromotionEditLogRepository extends JpaRepository<PromotionEditLog, UUID> {
    List<PromotionEditLog> findByPromotionIdOrderByCreatedAtDesc(UUID promotionId);
}
