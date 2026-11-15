package com.sfa.repository;

import com.sfa.entity.Shop;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {

    @Query("SELECT s FROM Shop s WHERE s.phoneNorm IN :phones OR s.whatsappNorm IN :phones")
    List<Shop> findByAnyPhone(@Param("phones") Collection<String> phones, Pageable pageable);

    List<Shop> findByTaxNorm(String taxNorm, Pageable pageable);

    @Query("""
        SELECT s FROM Shop s
        WHERE s.latitude BETWEEN :minLat AND :maxLat AND s.longitude BETWEEN :minLng AND :maxLng
        """)
    List<Shop> findInBox(@Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                         @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
                         Pageable pageable);
}
