package com.sfa.repository;

import com.sfa.entity.FixedAssetDepreciation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixedAssetDepreciationRepository extends JpaRepository<FixedAssetDepreciation, UUID> {
    boolean existsByFixedAssetIdAndPeriodDate(UUID fixedAssetId, LocalDate periodDate);
    List<FixedAssetDepreciation> findByFixedAssetIdOrderByPeriodDateDesc(UUID fixedAssetId);
}
