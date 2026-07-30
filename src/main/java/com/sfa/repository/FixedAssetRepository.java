package com.sfa.repository;

import com.sfa.entity.FixedAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {
    boolean existsByAssetCode(String assetCode);
    List<FixedAsset> findByActiveTrueOrderByPurchaseDateAsc();
    List<FixedAsset> findAllByOrderByPurchaseDateDesc();
}
